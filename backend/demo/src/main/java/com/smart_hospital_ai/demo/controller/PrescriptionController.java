package com.smart_hospital_ai.demo.controller;

import java.util.List;
import com.smart_hospital_ai.demo.dto.PrescriptionCreateRequest;
import com.smart_hospital_ai.demo.dto.PrescriptionHistoryResponse;
import com.smart_hospital_ai.demo.dto.PrescriptionResponse;
import com.smart_hospital_ai.demo.dto.PrescriptionUpdateRequest;
import com.smart_hospital_ai.demo.security.HospitalUserPrincipal;
import com.smart_hospital_ai.demo.service.PrescriptionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class PrescriptionController {

	private final PrescriptionService prescriptionService;

	public PrescriptionController(PrescriptionService prescriptionService) {
		this.prescriptionService = prescriptionService;
	}

	@PostMapping("/encounters/{encounterId}/prescriptions")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasRole('DOCTOR')")
	public PrescriptionResponse create(@PathVariable Long encounterId,
			@Valid @RequestBody PrescriptionCreateRequest request,
			@AuthenticationPrincipal HospitalUserPrincipal principal) {
		return prescriptionService.create(encounterId, request, principal);
	}

	@GetMapping("/encounters/{encounterId}/prescriptions")
	@PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'PATIENT')")
	public List<PrescriptionResponse> getForEncounter(@PathVariable Long encounterId,
			@AuthenticationPrincipal HospitalUserPrincipal principal) {
		return prescriptionService.getForEncounter(encounterId, principal);
	}

	@GetMapping("/prescriptions/{prescriptionId}")
	@PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'PATIENT')")
	public PrescriptionResponse getById(@PathVariable Long prescriptionId,
			@AuthenticationPrincipal HospitalUserPrincipal principal) {
		return prescriptionService.getById(prescriptionId, principal);
	}

	@GetMapping("/prescriptions/{prescriptionId}/history")
	@PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR')")
	public List<PrescriptionHistoryResponse> getHistory(@PathVariable Long prescriptionId,
			@AuthenticationPrincipal HospitalUserPrincipal principal) {
		return prescriptionService.getHistory(prescriptionId, principal);
	}

	@PutMapping("/prescriptions/{prescriptionId}")
	@PreAuthorize("hasRole('DOCTOR')")
	public PrescriptionResponse update(@PathVariable Long prescriptionId,
			@Valid @RequestBody PrescriptionUpdateRequest request,
			@AuthenticationPrincipal HospitalUserPrincipal principal) {
		return prescriptionService.update(prescriptionId, request, principal);
	}

	@PostMapping("/prescriptions/{prescriptionId}/finalize")
	@PreAuthorize("hasRole('DOCTOR')")
	public PrescriptionResponse finalizePrescription(@PathVariable Long prescriptionId,
			@AuthenticationPrincipal HospitalUserPrincipal principal) {
		return prescriptionService.finalizePrescription(prescriptionId, principal);
	}
}