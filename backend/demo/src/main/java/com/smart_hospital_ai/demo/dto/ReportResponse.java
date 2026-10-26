package com.smart_hospital_ai.demo.dto;

import java.time.OffsetDateTime;
import com.smart_hospital_ai.demo.entity.MedicalReportStatus;
import com.smart_hospital_ai.demo.entity.ReportGenerationMode;

public record ReportResponse(Long id, Long encounterId, MedicalReportStatus status,
		ReportGenerationMode generationMode, String content, Long authorUserId,
		Long reviewerUserId, OffsetDateTime createdAt, OffsetDateTime updatedAt,
		OffsetDateTime submittedAt, OffsetDateTime reviewedAt, String reviewerNotes) {
}