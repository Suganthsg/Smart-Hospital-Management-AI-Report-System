package com.smart_hospital_ai.demo.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smart_hospital_ai.demo.config.SecurityConfig;
import com.smart_hospital_ai.demo.dto.PatientRegistrationRequest;
import com.smart_hospital_ai.demo.dto.PatientResponse;
import com.smart_hospital_ai.demo.exception.ApiException;
import com.smart_hospital_ai.demo.service.PatientService;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.data.domain.Page;

@WebMvcTest(PatientController.class)
@Import(SecurityConfig.class)
class PatientAuthorizationTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private PatientService patientService;

	@Test
	void patientRegistrationIsPublic() throws Exception {
		when(patientService.register(any(PatientRegistrationRequest.class))).thenReturn(
			new PatientResponse(1L, "MRN-TEST", "new.patient@example.test", "New", "Patient", null, null, null));

		mockMvc.perform(post("/api/v1/patients")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"email":"new.patient@example.test","password":"Long-enough-password-42","firstName":"New","lastName":"Patient"}
					"""))
			.andExpect(status().isCreated());
	}

	@Test
	void patientSearchIsRestrictedToStaff() throws Exception {
		when(patientService.search(any(), any(Integer.class), any(Integer.class))).thenReturn(Page.empty());

		mockMvc.perform(get("/api/v1/patients").with(user("receptionist").roles("RECEPTIONIST")))
			.andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/patients").with(user("patient").roles("PATIENT")))
			.andExpect(status().isForbidden());
	}

	@Test
	void anonymousPatientSearchRequiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/v1/patients"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void patientDeletionIsAdminOnly() throws Exception {
		mockMvc.perform(delete("/api/v1/patients/12"))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(delete("/api/v1/patients/12").with(user("receptionist").roles("RECEPTIONIST")))
			.andExpect(status().isForbidden());
		mockMvc.perform(delete("/api/v1/patients/12").with(user("admin").roles("ADMIN")))
			.andExpect(status().isNoContent());

		verify(patientService).delete(12L);
	}

	@Test
	void missingPatientDeletionReturnsNotFound() throws Exception {
		doThrow(new ApiException(HttpStatus.NOT_FOUND, "Patient not found"))
			.when(patientService).delete(999L);

		mockMvc.perform(delete("/api/v1/patients/999").with(user("admin").roles("ADMIN")))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("Patient not found"));
	}

	@Test
	void patientDeletionReturnsConflictWhenMedicalHistoryIsLinked() throws Exception {
		doThrow(new ApiException(HttpStatus.CONFLICT,
			"Patient cannot be deleted because clinical encounters are linked. Medical history has been preserved."))
			.when(patientService).delete(12L);

		mockMvc.perform(delete("/api/v1/patients/12").with(user("admin").roles("ADMIN")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value(
				"Patient cannot be deleted because clinical encounters are linked. Medical history has been preserved."));
	}
}