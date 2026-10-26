package com.smart_hospital_ai.demo.dto;

import java.time.LocalTime;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record DoctorAvailabilityRequest(
		@Min(1) @Max(7) short dayOfWeek,
		@NotNull LocalTime startTime,
		@NotNull LocalTime endTime) {
}