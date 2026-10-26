package com.smart_hospital_ai.demo.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import com.smart_hospital_ai.demo.entity.EncounterStatus;

public record EncounterResponse(
		Long id,
		Long patientId,
		String medicalRecordNumber,
		Long doctorId,
		String doctorName,
		Long appointmentId,
		LocalDate encounterDate,
		String clinicalNotes,
		String diagnosis,
		EncounterStatus status,
		Long createdByUserId,
		OffsetDateTime createdAt,
		OffsetDateTime updatedAt) {
}