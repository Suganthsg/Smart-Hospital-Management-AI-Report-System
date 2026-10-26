package com.smart_hospital_ai.demo.dto;

import java.time.OffsetDateTime;
import java.util.List;
import com.smart_hospital_ai.demo.entity.PrescriptionHistoryEventType;
import com.smart_hospital_ai.demo.entity.PrescriptionStatus;

public record PrescriptionHistoryResponse(Long id, Long changedByUserId,
		PrescriptionHistoryEventType eventType, PrescriptionStatus status,
		OffsetDateTime occurredAt, List<PrescriptionHistoryItemResponse> items) {
}