package com.smart_hospital_ai.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LabResultCreateRequest(
		@NotBlank @Size(max = 10000) String resultValue,
		@Size(max = 100) String units,
		@Size(max = 250) String referenceRange,
		@Size(max = 5000) String notes) {
}