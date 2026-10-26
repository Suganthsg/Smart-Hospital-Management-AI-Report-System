package com.smart_hospital_ai.demo.dto;

import java.time.OffsetDateTime;

public record VerifiedLabResultInput(String testName, String resultValue, String units,
		String referenceRange, OffsetDateTime verifiedAt) {
}