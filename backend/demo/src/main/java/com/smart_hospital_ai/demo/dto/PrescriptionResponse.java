package com.smart_hospital_ai.demo.dto;

import java.time.OffsetDateTime;
import java.util.List;
import com.smart_hospital_ai.demo.entity.PrescriptionStatus;

public record PrescriptionResponse(Long id, Long encounterId, PrescriptionStatus status,
		Long prescribedByUserId, OffsetDateTime createdAt, OffsetDateTime updatedAt,
		OffsetDateTime finalizedAt, List<PrescriptionItemResponse> items) {
}