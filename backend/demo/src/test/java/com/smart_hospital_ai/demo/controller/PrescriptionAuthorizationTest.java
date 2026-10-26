package com.smart_hospital_ai.demo.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.List;
import com.smart_hospital_ai.demo.config.SecurityConfig;
import com.smart_hospital_ai.demo.dto.PrescriptionCreateRequest;
import com.smart_hospital_ai.demo.dto.PrescriptionResponse;
import com.smart_hospital_ai.demo.entity.PrescriptionStatus;
import com.smart_hospital_ai.demo.service.PrescriptionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PrescriptionController.class)
@Import(SecurityConfig.class)
class PrescriptionAuthorizationTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private PrescriptionService prescriptionService;

	@Test
	void onlyDoctorCanCreatePrescription() throws Exception {
		when(prescriptionService.create(eq(5L), any(PrescriptionCreateRequest.class), isNull()))
			.thenReturn(new PrescriptionResponse(7L, 5L, PrescriptionStatus.DRAFT, 9L,
				OffsetDateTime.now(), OffsetDateTime.now(), null, List.of()));
		String body = """
			{"items":[{"medicineName":"Example","dosage":"10 mg","route":"oral","frequency":"once daily","duration":"5 days","quantity":5,"instructions":"Take with water"}]}
			""";

		mockMvc.perform(post("/api/v1/encounters/5/prescriptions")
				.with(user("doctor").roles("DOCTOR"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isCreated());
		mockMvc.perform(post("/api/v1/encounters/5/prescriptions")
				.with(user("reception").roles("RECEPTIONIST"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isForbidden());
		mockMvc.perform(post("/api/v1/encounters/5/prescriptions")
				.with(user("lab").roles("LAB_TECHNICIAN"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isForbidden());
	}

	@Test
	void invalidPrescriptionItemIsRejectedBeforeServiceCall() throws Exception {
		String body = """
			{"items":[{"medicineName":"Medicine","dosage":"5 mg","route":"oral","frequency":"daily","duration":"3 days","quantity":0}]}
			""";

		mockMvc.perform(post("/api/v1/encounters/5/prescriptions")
				.with(user("doctor").roles("DOCTOR"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isBadRequest());
		verifyNoInteractions(prescriptionService);
	}
}