package com.smart_hospital_ai.demo.dto;

import java.time.LocalDate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record EncounterCreateRequest(
		@NotNull @Positive Long patientId,
		@Positive Long appointmentId,
		@NotNull @PastOrPresent LocalDate encounterDate,
		@NotBlank @Size(max = 50000) String clinicalNotes,
		@NotBlank @Size(max = 2000) String diagnosis) {
}