package com.smart_hospital_ai.demo.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import com.smart_hospital_ai.demo.config.SecurityConfig;
import com.smart_hospital_ai.demo.dto.ReportResponse;
import com.smart_hospital_ai.demo.dto.ReportUpdateRequest;
import com.smart_hospital_ai.demo.entity.MedicalReportStatus;
import com.smart_hospital_ai.demo.entity.ReportGenerationMode;
import com.smart_hospital_ai.demo.service.MedicalReportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MedicalReportController.class)
@Import(SecurityConfig.class)
class MedicalReportAuthorizationTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private MedicalReportService medicalReportService;

	@Test
	void onlyDoctorCanGenerateAndEditReportDrafts() throws Exception {
		when(medicalReportService.generateDraft(eq(10L), isNull())).thenReturn(draftResponse());
		when(medicalReportService.editDraft(eq(20L), any(ReportUpdateRequest.class), isNull()))
			.thenReturn(draftResponse());
		String editBody = "{\"content\":\"Edited clinician draft\"}";

		mockMvc.perform(post("/api/v1/encounters/10/reports/generate")
				.with(user("doctor").roles("DOCTOR")))
			.andExpect(status().isCreated());
		mockMvc.perform(post("/api/v1/encounters/10/reports/generate")
				.with(user("reception").roles("RECEPTIONIST")))
			.andExpect(status().isForbidden());
		mockMvc.perform(put("/api/v1/reports/20/draft")
				.with(user("lab").roles("LAB_TECHNICIAN"))
				.contentType(MediaType.APPLICATION_JSON).content(editBody))
			.andExpect(status().isForbidden());
	}

	@Test
	void onlyDoctorCanApproveAndPatientsCannotReadHistoryRoute() throws Exception {
		when(medicalReportService.approve(eq(20L), any(), isNull())).thenReturn(draftResponse());

		mockMvc.perform(post("/api/v1/reports/20/approve")
				.with(user("doctor").roles("DOCTOR"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/reports/20/approve")
				.with(user("reception").roles("RECEPTIONIST"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/reports/20/history")
				.with(user("patient").roles("PATIENT")))
			.andExpect(status().isForbidden());
	}

	@Test
	void blankManualDraftContentFailsRequestValidation() throws Exception {
		mockMvc.perform(put("/api/v1/reports/20/draft")
				.with(user("doctor").roles("DOCTOR"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"content\":\" \"}"))
			.andExpect(status().isBadRequest());
		verifyNoInteractions(medicalReportService);
	}

	private static ReportResponse draftResponse() {
		return new ReportResponse(20L, 10L, MedicalReportStatus.DRAFT, ReportGenerationMode.MOCK,
			"Recorded content", 7L, null, OffsetDateTime.now(), OffsetDateTime.now(), null, null, null);
	}
}