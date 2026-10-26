package com.smart_hospital_ai.demo.service;

import com.smart_hospital_ai.demo.dto.StaffUserCreateRequest;
import com.smart_hospital_ai.demo.dto.StaffUserResponse;
import com.smart_hospital_ai.demo.entity.UserAccount;
import com.smart_hospital_ai.demo.entity.UserRole;
import com.smart_hospital_ai.demo.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StaffUserService {

	private final UserAccountService userAccountService;

	public StaffUserService(UserAccountService userAccountService) {
		this.userAccountService = userAccountService;
	}

	@Transactional
	public StaffUserResponse create(StaffUserCreateRequest request) {
		if (request.role() == UserRole.PATIENT || request.role() == UserRole.DOCTOR) {
			throw new ApiException(HttpStatus.BAD_REQUEST,
				"Patient and doctor accounts must be created with their profile endpoints");
		}
		UserAccount user = userAccountService.create(request.email(), request.password(), request.role());
		return new StaffUserResponse(user.getId(), user.getEmail(), user.getRole(), user.isActive());
	}
}