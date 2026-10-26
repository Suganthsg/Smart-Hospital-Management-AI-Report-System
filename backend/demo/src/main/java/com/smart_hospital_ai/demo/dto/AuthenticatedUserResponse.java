package com.smart_hospital_ai.demo.dto;

import com.smart_hospital_ai.demo.entity.UserRole;

public record AuthenticatedUserResponse(Long id, String email, UserRole role) {
}