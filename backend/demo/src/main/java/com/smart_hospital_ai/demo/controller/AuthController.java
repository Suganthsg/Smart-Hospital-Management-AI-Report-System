package com.smart_hospital_ai.demo.controller;

import com.smart_hospital_ai.demo.dto.AuthenticatedUserResponse;
import com.smart_hospital_ai.demo.security.HospitalUserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

	@GetMapping("/me")
	public AuthenticatedUserResponse currentUser(@AuthenticationPrincipal HospitalUserPrincipal principal) {
		return new AuthenticatedUserResponse(principal.getUserId(), principal.getUsername(), principal.getRole());
	}
}