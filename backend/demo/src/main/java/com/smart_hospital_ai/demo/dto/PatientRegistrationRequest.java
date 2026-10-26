package com.smart_hospital_ai.demo.dto;

import java.time.LocalDate;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PatientRegistrationRequest(
		@NotBlank @Email @Size(max = 320) String email,
		@NotBlank @Size(min = 12, max = 72) String password,
		@NotBlank @Size(max = 100) String firstName,
		@NotBlank @Size(max = 100) String lastName,
		@Past LocalDate dateOfBirth,
		@Pattern(regexp = "^$|^\\+?[0-9(). -]{7,32}$") String phone) {
}