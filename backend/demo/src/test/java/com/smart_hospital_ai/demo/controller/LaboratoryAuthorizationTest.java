package com.smart_hospital_ai.demo.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.List;
import com.smart_hospital_ai.demo.config.SecurityConfig;
import com.smart_hospital_ai.demo.dto.LabOrderResponse;
import com.smart_hospital_ai.demo.dto.LabResultCreateRequest;
import com.smart_hospital_ai.demo.dto.LabResultResponse;
import com.smart_hospital_ai.demo.entity.LabOrderStatus;
import com.smart_hospital_ai.demo.entity.LabResultStatus;
import com.smart_hospital_ai.demo.service.LabOrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(LaboratoryController.class)
@Import(SecurityConfig.class)
class LaboratoryAuthorizationTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private LabOrderService labOrderService;

	@Test
	void onlyLabTechniciansCanUseWorklistAndEnterResults() throws Exception {
		when(labOrderService.worklist(isNull(), eq(0), eq(20), isNull())).thenReturn(Page.empty());
		when(labOrderService.enterResult(any(Long.class), any(LabResultCreateRequest.class), isNull()))
			.thenReturn(new LabResultResponse(3L, 8L, 1, "4.2", null, null, null,
				LabResultStatus.ENTERED, 12L, null, null, null, OffsetDateTime.now()));
		String body = "{\"resultValue\":\"4.2\"}";

		mockMvc.perform(get("/api/v1/lab-orders/worklist")
				.with(user("lab").roles("LAB_TECHNICIAN")))
			.andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/lab-orders/worklist")
				.with(user("doctor").roles("DOCTOR")))
			.andExpect(status().isForbidden());
		mockMvc.perform(post("/api/v1/lab-orders/8/results")
				.with(user("lab").roles("LAB_TECHNICIAN"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isCreated());
		mockMvc.perform(post("/api/v1/lab-orders/8/results")
				.with(user("reception").roles("RECEPTIONIST"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isForbidden());
	}

	@Test
	void onlyDoctorsCanVerifyResults() throws Exception {
		when(labOrderService.verifyResult(any(Long.class), any(Long.class), isNull()))
			.thenReturn(new LabResultResponse(3L, 8L, 1, "4.2", null, null, null,
				LabResultStatus.VERIFIED, 12L, 14L, OffsetDateTime.now(), null, OffsetDateTime.now()));

		mockMvc.perform(post("/api/v1/lab-orders/8/results/3/verify")
				.with(user("doctor").roles("DOCTOR")))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/lab-orders/8/results/3/verify")
				.with(user("lab").roles("LAB_TECHNICIAN")))
			.andExpect(status().isForbidden());
	}

	@Test
	void blankLabResultValueIsRejectedBeforeServiceCall() throws Exception {
		mockMvc.perform(post("/api/v1/lab-orders/8/results")
				.with(user("lab").roles("LAB_TECHNICIAN"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"resultValue\":\" \"}"))
			.andExpect(status().isBadRequest());
		verifyNoInteractions(labOrderService);
	}
}