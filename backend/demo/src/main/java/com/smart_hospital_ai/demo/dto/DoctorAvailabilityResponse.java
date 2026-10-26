package com.smart_hospital_ai.demo.dto;

import java.time.LocalTime;

public record DoctorAvailabilityResponse(Long id, Long doctorId, short dayOfWeek,
		LocalTime startTime, LocalTime endTime) {
}