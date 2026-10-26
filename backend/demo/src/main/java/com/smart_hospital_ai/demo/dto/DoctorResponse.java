package com.smart_hospital_ai.demo.dto;

public record DoctorResponse(Long id, String email, String licenseNumber, String firstName,
		String lastName, String specialization, String phone) {
}