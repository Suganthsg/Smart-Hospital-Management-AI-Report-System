package com.smart_hospital_ai.demo.dto;

import com.smart_hospital_ai.demo.entity.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StaffUserCreateRequest(
		@NotBlank @Email @Size(max = 320) String email,
		@NotBlank @Size(min = 12, max = 72) String password,
		@NotNull UserRole role) {
}