package com.smart_hospital_ai.demo.controller;

import com.smart_hospital_ai.demo.dto.DatabaseHealthResponse;
import com.smart_hospital_ai.demo.service.DatabaseHealthService;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/health")
public class DatabaseHealthController {

	private final DatabaseHealthService databaseHealthService;

	public DatabaseHealthController(DatabaseHealthService databaseHealthService) {
		this.databaseHealthService = databaseHealthService;
	}

	@GetMapping("/database")
	public ResponseEntity<DatabaseHealthResponse> getDatabaseHealth() {
		try {
			databaseHealthService.checkConnection();
			return ResponseEntity.ok(new DatabaseHealthResponse("UP", "UP"));
		} catch (DataAccessException exception) {
			return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
				.body(new DatabaseHealthResponse("DOWN", "DOWN"));
		}
	}
}