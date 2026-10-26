package com.smart_hospital_ai.demo.dto;

import java.time.OffsetDateTime;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AppointmentCreateRequest(
		Long patientId,
		Long doctorId,
		@NotNull @Future OffsetDateTime startAt,
		@NotNull @Min(15) @Max(120) Integer durationMinutes,
		@Size(max = 500) String reason) {
}