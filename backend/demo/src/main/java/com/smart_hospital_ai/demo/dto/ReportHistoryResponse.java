package com.smart_hospital_ai.demo.dto;

import java.time.OffsetDateTime;
import com.smart_hospital_ai.demo.entity.MedicalReportHistoryEventType;
import com.smart_hospital_ai.demo.entity.MedicalReportStatus;

public record ReportHistoryResponse(Long id, Long actorUserId, MedicalReportHistoryEventType eventType,
		MedicalReportStatus status, String contentSnapshot, String eventNote, OffsetDateTime occurredAt) {
}