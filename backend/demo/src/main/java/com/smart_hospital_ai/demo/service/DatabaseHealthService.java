package com.smart_hospital_ai.demo.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class DatabaseHealthService {

	private final JdbcTemplate jdbcTemplate;

	public DatabaseHealthService(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public void checkConnection() {
		jdbcTemplate.queryForObject("SELECT 1", Integer.class);
	}
}