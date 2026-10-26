package com.smart_hospital_ai.demo.dto;

import java.time.OffsetDateTime;
import com.smart_hospital_ai.demo.entity.AppointmentStatus;

public record AppointmentResponse(
		Long id,
		Long patientId,
		String medicalRecordNumber,
		Long doctorId,
		String doctorName,
		OffsetDateTime startAt,
		OffsetDateTime endAt,
		AppointmentStatus status,
		String reason,
		OffsetDateTime cancelledAt) {
}