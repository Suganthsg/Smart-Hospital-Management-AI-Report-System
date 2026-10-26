package com.smart_hospital_ai.demo.dto;

import java.time.OffsetDateTime;
import com.smart_hospital_ai.demo.entity.LabResultStatus;

public record LabResultResponse(Long id, Long labOrderId, int resultVersion, String resultValue,
		String units, String referenceRange, String notes, LabResultStatus status,
		Long enteredByUserId, Long verifiedByUserId, OffsetDateTime verifiedAt,
		Long previousResultId, OffsetDateTime createdAt) {
}