package com.smart_hospital_ai.demo.controller;

import com.smart_hospital_ai.demo.dto.PatientRegistrationRequest;
import com.smart_hospital_ai.demo.dto.PatientResponse;
import com.smart_hospital_ai.demo.dto.PatientUpdateRequest;
import com.smart_hospital_ai.demo.dto.PageResponse;
import com.smart_hospital_ai.demo.security.HospitalUserPrincipal;
import com.smart_hospital_ai.demo.service.PatientService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/patients")
public class PatientController {

	private final PatientService patientService;

	public PatientController(PatientService patientService) {
		this.patientService = patientService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public PatientResponse register(@Valid @RequestBody PatientRegistrationRequest request) {
		return patientService.register(request);
	}

	@GetMapping
	@PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
	public PageResponse<PatientResponse> search(@RequestParam(required = false) String q,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return PageResponse.from(patientService.search(q, page, size));
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'RECEPTIONIST', 'LAB_TECHNICIAN', 'PATIENT')")
	public PatientResponse getById(@PathVariable Long id,
			@AuthenticationPrincipal HospitalUserPrincipal principal) {
		return patientService.getById(id, principal);
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST', 'PATIENT')")
	public PatientResponse update(@PathVariable Long id,
			@Valid @RequestBody PatientUpdateRequest request,
			@AuthenticationPrincipal HospitalUserPrincipal principal) {
		return patientService.update(id, request, principal);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize("hasRole('ADMIN')")
	public void delete(@PathVariable Long id) {
		patientService.delete(id);
	}

	@PatchMapping("/{patientId}/assigned-doctor/{doctorId}")
	@PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
	public PatientResponse assignDoctor(@PathVariable Long patientId, @PathVariable Long doctorId) {
		return patientService.assignDoctor(patientId, doctorId);
	}
}