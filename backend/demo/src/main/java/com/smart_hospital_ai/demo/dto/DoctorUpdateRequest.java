package com.smart_hospital_ai.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DoctorUpdateRequest(
		@NotBlank @Size(max = 64) String licenseNumber,
		@NotBlank @Size(max = 100) String firstName,
		@NotBlank @Size(max = 100) String lastName,
		@NotBlank @Size(max = 150) String specialization,
		@Pattern(regexp = "^$|^\\+?[0-9(). -]{7,32}$") String phone) {
}