package com.smart_hospital_ai.demo.controller;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smart_hospital_ai.demo.config.SecurityConfig;
import com.smart_hospital_ai.demo.exception.ApiException;
import com.smart_hospital_ai.demo.service.DoctorAvailabilityService;
import com.smart_hospital_ai.demo.service.DoctorService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DoctorController.class)
@Import(SecurityConfig.class)
class DoctorAuthorizationTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private DoctorService doctorService;

	@MockitoBean
	private DoctorAvailabilityService availabilityService;

	@Test
	void doctorDeletionIsAdminOnly() throws Exception {
		mockMvc.perform(delete("/api/v1/doctors/12"))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(delete("/api/v1/doctors/12").with(user("doctor").roles("DOCTOR")))
			.andExpect(status().isForbidden());
		mockMvc.perform(delete("/api/v1/doctors/12").with(user("admin").roles("ADMIN")))
			.andExpect(status().isNoContent());

		verify(doctorService).delete(12L);
	}

	@Test
	void missingDoctorDeletionReturnsNotFound() throws Exception {
		doThrow(new ApiException(HttpStatus.NOT_FOUND, "Doctor not found"))
			.when(doctorService).delete(999L);

		mockMvc.perform(delete("/api/v1/doctors/999").with(user("admin").roles("ADMIN")))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("Doctor not found"));
	}
}
