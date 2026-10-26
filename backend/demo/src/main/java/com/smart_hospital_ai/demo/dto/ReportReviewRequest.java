package com.smart_hospital_ai.demo.dto;

import jakarta.validation.constraints.Size;

public record ReportReviewRequest(@Size(max = 5000) String notes) {
}