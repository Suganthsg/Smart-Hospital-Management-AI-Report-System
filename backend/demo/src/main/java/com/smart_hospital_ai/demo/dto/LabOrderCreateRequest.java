package com.smart_hospital_ai.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LabOrderCreateRequest(@NotBlank @Size(max = 200) String testName) {
}