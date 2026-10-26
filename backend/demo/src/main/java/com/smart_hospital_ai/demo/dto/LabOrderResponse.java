package com.smart_hospital_ai.demo.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import com.smart_hospital_ai.demo.entity.LabOrderStatus;

public record LabOrderResponse(Long id, Long encounterId, Long patientId, String medicalRecordNumber,
		Long doctorId, String doctorName, LocalDate encounterDate, String testName,
		LabOrderStatus status, Long orderedByUserId, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
}