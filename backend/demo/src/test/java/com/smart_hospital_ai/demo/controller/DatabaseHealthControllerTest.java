package com.smart_hospital_ai.demo.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smart_hospital_ai.demo.dto.DatabaseHealthResponse;
import com.smart_hospital_ai.demo.service.DatabaseHealthService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

class DatabaseHealthControllerTest {

	private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
	private final DatabaseHealthService databaseHealthService = new DatabaseHealthService(jdbcTemplate);
	private final DatabaseHealthController controller = new DatabaseHealthController(databaseHealthService);

	@Test
	void returnsUpOnlyAfterDatabaseQuerySucceeds() {
		when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(1);

		ResponseEntity<DatabaseHealthResponse> response = controller.getDatabaseHealth();

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertEquals(new DatabaseHealthResponse("UP", "UP"), response.getBody());
		verify(jdbcTemplate).queryForObject("SELECT 1", Integer.class);
	}

	@Test
	void returnsSafeUnavailableResponseWhenDatabaseQueryFails() {
		when(jdbcTemplate.queryForObject("SELECT 1", Integer.class))
			.thenThrow(new DataAccessResourceFailureException("database unavailable"));

		ResponseEntity<DatabaseHealthResponse> response = controller.getDatabaseHealth();

		assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
		assertEquals(new DatabaseHealthResponse("DOWN", "DOWN"), response.getBody());
	}
}