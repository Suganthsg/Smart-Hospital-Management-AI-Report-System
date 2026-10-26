package com.smart_hospital_ai.demo.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.List;
import com.smart_hospital_ai.demo.config.SecurityConfig;
import com.smart_hospital_ai.demo.dto.AppointmentCreateRequest;
import com.smart_hospital_ai.demo.dto.AppointmentResponse;
import com.smart_hospital_ai.demo.entity.AppointmentStatus;
import com.smart_hospital_ai.demo.entity.UserAccount;
import com.smart_hospital_ai.demo.entity.UserRole;
import com.smart_hospital_ai.demo.exception.ApiException;
import com.smart_hospital_ai.demo.security.HospitalUserPrincipal;
import com.smart_hospital_ai.demo.service.AppointmentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AppointmentController.class)
@Import(SecurityConfig.class)
class AppointmentAuthorizationTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AppointmentService appointmentService;

	@Test
	void appointmentRoutesRequireAnAuthenticatedAppointmentRole() throws Exception {
		mockMvc.perform(get("/api/v1/appointments"))
			.andExpect(status().isUnauthorized());

		mockMvc.perform(get("/api/v1/appointments")
				.with(user(principal(UserRole.LAB_TECHNICIAN))))
			.andExpect(status().isForbidden());
 	}

	@Test
	void receptionistCanCreateAppointmentAndPatientCanUseAppointmentRoutes() throws Exception {
		OffsetDateTime startAt = OffsetDateTime.parse("2035-06-10T09:00:00Z");
		when(appointmentService.create(any(AppointmentCreateRequest.class), any(HospitalUserPrincipal.class)))
			.thenReturn(new AppointmentResponse(1L, 2L, "MRN-TEST", 3L, "Test Doctor", startAt,
				startAt.plusMinutes(30), AppointmentStatus.SCHEDULED, null, null));
		when(appointmentService.search(any(), any(), any(), any(), any(), any(Integer.class),
			any(Integer.class), any(HospitalUserPrincipal.class))).thenReturn(Page.empty());
		String body = """
			{"patientId":2,"doctorId":3,"startAt":"2035-06-10T09:00:00Z","durationMinutes":30}
			""";

		mockMvc.perform(post("/api/v1/appointments")
				.with(user(principal(UserRole.RECEPTIONIST)))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isCreated());
		mockMvc.perform(get("/api/v1/appointments")
				.with(user(principal(UserRole.PATIENT))))
			.andExpect(status().isOk());
	}

	@Test
	void patientCanSubmitBookingWithoutPatientId() throws Exception {
		OffsetDateTime startAt = OffsetDateTime.parse("2035-06-10T09:00:00Z");
		when(appointmentService.create(any(AppointmentCreateRequest.class), any(HospitalUserPrincipal.class)))
			.thenReturn(new AppointmentResponse(1L, 2L, "MRN-TEST", 3L, "Test Doctor", startAt,
				startAt.plusMinutes(30), AppointmentStatus.SCHEDULED, null, null));

		mockMvc.perform(post("/api/v1/appointments")
				.with(user(principal(UserRole.PATIENT)))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"doctorId":3,"startAt":"2035-06-10T09:00:00Z","durationMinutes":30}
					"""))
			.andExpect(status().isCreated());

		verify(appointmentService).create(
			argThat(request -> request.patientId() == null),
			argThat(authenticated -> authenticated.getRole() == UserRole.PATIENT));
	}

	@Test
	void adminAndPatientCanListAppointmentsAsPagedResponseDtos() throws Exception {
		OffsetDateTime startAt = OffsetDateTime.parse("2035-06-10T09:00:00Z");
		AppointmentResponse appointment = new AppointmentResponse(1L, 2L, "MRN-TEST", 3L,
			"Test Doctor", startAt, startAt.plusMinutes(30), AppointmentStatus.SCHEDULED, null, null);
		when(appointmentService.search(any(), any(), any(), any(), any(), any(Integer.class),
			any(Integer.class), any(HospitalUserPrincipal.class)))
			.thenReturn(new PageImpl<>(List.of(appointment), PageRequest.of(0, 20), 1));

		for (UserRole role : new UserRole[] {UserRole.ADMIN, UserRole.PATIENT}) {
			mockMvc.perform(get("/api/v1/appointments")
					.with(user(principal(role))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].id").value(1))
				.andExpect(jsonPath("$.content[0].medicalRecordNumber").value("MRN-TEST"))
				.andExpect(jsonPath("$.content[0].doctorName").value("Test Doctor"))
				.andExpect(jsonPath("$.totalElements").value(1));
		}
	}

	@Test
	void invalidAppointmentRequestReturnsFieldSpecificBadRequest() throws Exception {
		mockMvc.perform(post("/api/v1/appointments")
				.with(user(principal(UserRole.PATIENT)))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"doctorId":3,"startAt":"2035-06-10T09:00:00Z","durationMinutes":10}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(content().string(containsString("durationMinutes")));
	}

	@Test
	void cancellationWithin24HoursReturnsClearConflictMessage() throws Exception {
		when(appointmentService.cancel(org.mockito.ArgumentMatchers.eq(70L),
			any(HospitalUserPrincipal.class)))
			.thenThrow(new ApiException(HttpStatus.CONFLICT,
				"Appointments can only be cancelled more than 24 hours before their start time"));

		mockMvc.perform(patch("/api/v1/appointments/70/cancel")
				.with(user(principal(UserRole.PATIENT))))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message")
				.value("Appointments can only be cancelled more than 24 hours before their start time"));
	}

	private static HospitalUserPrincipal principal(UserRole role) {
		return new HospitalUserPrincipal(new UserAccount("test@example.test", "not-used", role));
	}
}