package com.smart_hospital_ai.demo.controller;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import com.smart_hospital_ai.demo.config.SecurityConfig;
import com.smart_hospital_ai.demo.service.DatabaseHealthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DatabaseHealthController.class)
@Import(SecurityConfig.class)
class DatabaseHealthSecurityTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private DatabaseHealthService databaseHealthService;

	@Test
	void databaseHealthIsAvailableWithoutAuthentication() throws Exception {
		doNothing().when(databaseHealthService).checkConnection();

		mockMvc.perform(get("/api/v1/health/database"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("UP"))
			.andExpect(jsonPath("$.database").value("UP"));
	}

	@Test
	void databaseFailureReturnsSafeUnavailableResponse() throws Exception {
		doThrow(new DataAccessResourceFailureException("database unavailable"))
			.when(databaseHealthService).checkConnection();

		mockMvc.perform(get("/api/v1/health/database"))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.status").value("DOWN"))
			.andExpect(jsonPath("$.database").value("DOWN"));
	}

	@Test
	void otherEndpointsRequireAuthentication() throws Exception {
		mockMvc.perform(get("/api/v1/private"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void frontendPreflightAllowsBasicAuthorizationHeader() throws Exception {
		mockMvc.perform(options("/api/v1/auth/me")
				.header("Origin", "http://localhost:5173")
				.header("Access-Control-Request-Method", "GET")
				.header("Access-Control-Request-Headers", "authorization"))
			.andExpect(status().isOk())
			.andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
			.andExpect(header().string("Access-Control-Allow-Headers", org.hamcrest.Matchers.containsStringIgnoringCase("authorization")));
	}
}