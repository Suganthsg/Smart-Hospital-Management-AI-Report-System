package com.smart_hospital_ai.demo.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import com.smart_hospital_ai.demo.config.SecurityConfig;
import com.smart_hospital_ai.demo.dto.EncounterCreateRequest;
import com.smart_hospital_ai.demo.dto.EncounterResponse;
import com.smart_hospital_ai.demo.entity.EncounterStatus;
import com.smart_hospital_ai.demo.service.EncounterService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(EncounterController.class)
@Import(SecurityConfig.class)
class EncounterAuthorizationTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private EncounterService encounterService;

	@Test
	void anonymousAndReceptionistCannotReadClinicalEncounters() throws Exception {
		mockMvc.perform(get("/api/v1/encounters"))
			.andExpect(status().isUnauthorized());

		mockMvc.perform(get("/api/v1/encounters").with(org.springframework.security.test.web.servlet.request
			.SecurityMockMvcRequestPostProcessors.user("reception").roles("RECEPTIONIST")))
			.andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser(roles = "PATIENT")
	void patientCanReadEncounterEndpoints() throws Exception {
		EncounterResponse response = new EncounterResponse(1L, 2L, "MRN-1", 3L,
			"Assigned Doctor", null, LocalDate.now(), "Notes", "Diagnosis",
			EncounterStatus.DRAFT, 4L, null, null);
		when(encounterService.getById(eq(1L), isNull())).thenReturn(response);
		when(encounterService.search(any(), any(), any(), any(), any(), anyInt(), anyInt(), isNull()))
			.thenReturn(Page.empty());

		mockMvc.perform(get("/api/v1/encounters/1"))
			.andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/encounters"))
			.andExpect(status().isOk());
	}

	@Test
	@WithMockUser(roles = "ADMIN")
	void adminCannotCreateClinicalEncounter() throws Exception {
		String body = """
			{"patientId":2,"encounterDate":"2026-10-20","clinicalNotes":"Notes","diagnosis":"Diagnosis"}
			""";

		mockMvc.perform(post("/api/v1/encounters")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser(roles = "DOCTOR")
	void doctorCanCreateClinicalEncounter() throws Exception {
		when(encounterService.create(any(EncounterCreateRequest.class), isNull()))
			.thenReturn(new EncounterResponse(1L, 2L, "MRN-1", 3L, "Doctor", null,
				LocalDate.now(), "Notes", "Diagnosis", EncounterStatus.DRAFT, 4L, null, null));
		String body = """
			{"patientId":2,"encounterDate":"2026-10-20","clinicalNotes":"Notes","diagnosis":"Diagnosis"}
			""";

		mockMvc.perform(post("/api/v1/encounters")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isCreated());
	}
}