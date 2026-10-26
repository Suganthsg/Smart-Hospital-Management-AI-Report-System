package com.smart_hospital_ai.demo.controller;

import com.smart_hospital_ai.demo.dto.StaffUserCreateRequest;
import com.smart_hospital_ai.demo.dto.StaffUserResponse;
import com.smart_hospital_ai.demo.service.StaffUserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

	private final StaffUserService staffUserService;

	public AdminUserController(StaffUserService staffUserService) {
		this.staffUserService = staffUserService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public StaffUserResponse create(@Valid @RequestBody StaffUserCreateRequest request) {
		return staffUserService.create(request);
	}
}