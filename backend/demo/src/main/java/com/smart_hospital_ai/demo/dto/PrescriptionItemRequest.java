package com.smart_hospital_ai.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record PrescriptionItemRequest(
		@NotBlank @Size(max = 200) String medicineName,
		@NotBlank @Size(max = 200) String dosage,
		@NotBlank @Size(max = 100) String route,
		@NotBlank @Size(max = 200) String frequency,
		@NotBlank @Size(max = 100) String duration,
		@Positive int quantity,
		@Size(max = 4000) String instructions) {
}