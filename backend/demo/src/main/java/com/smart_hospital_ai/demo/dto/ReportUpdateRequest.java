package com.smart_hospital_ai.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReportUpdateRequest(@NotBlank @Size(max = 100000) String content) {
}