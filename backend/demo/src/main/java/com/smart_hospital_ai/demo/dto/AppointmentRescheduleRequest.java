package com.smart_hospital_ai.demo.dto;

import java.time.OffsetDateTime;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AppointmentRescheduleRequest(
		@NotNull @Future OffsetDateTime startAt,
		@NotNull @Min(15) @Max(120) Integer durationMinutes) {
}