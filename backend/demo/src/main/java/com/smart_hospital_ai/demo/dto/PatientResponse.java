package com.smart_hospital_ai.demo.dto;

import java.time.LocalDate;

public record PatientResponse(Long id, String medicalRecordNumber, String email, String firstName,
		String lastName, LocalDate dateOfBirth, String phone, Long assignedDoctorId) {
}