package com.smart_hospital_ai.demo.controller;

import com.smart_hospital_ai.demo.dto.EncounterCreateRequest;
import com.smart_hospital_ai.demo.dto.EncounterResponse;
import com.smart_hospital_ai.demo.dto.EncounterUpdateRequest;
import com.smart_hospital_ai.demo.dto.PageResponse;
import com.smart_hospital_ai.demo.entity.EncounterStatus;
import com.smart_hospital_ai.demo.security.HospitalUserPrincipal;
import com.smart_hospital_ai.demo.service.EncounterService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/encounters")
@PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'PATIENT')")
public class EncounterController {

	private final EncounterService encounterService;

	public EncounterController(EncounterService encounterService) {
		this.encounterService = encounterService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasRole('DOCTOR')")
	public EncounterResponse create(@Valid @RequestBody EncounterCreateRequest request,
			@AuthenticationPrincipal HospitalUserPrincipal principal) {
		return encounterService.create(request, principal);
	}

	@GetMapping
	public PageResponse<EncounterResponse> search(
			@RequestParam(required = false) EncounterStatus status,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
			@RequestParam(required = false) Long patientId,
			@RequestParam(required = false) Long doctorId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			@AuthenticationPrincipal HospitalUserPrincipal principal) {
		Page<EncounterResponse> results = encounterService.search(status, fromDate, toDate,
			patientId, doctorId, page, size, principal);
		return PageResponse.from(results);
	}

	@GetMapping("/{id}")
	public EncounterResponse getById(@PathVariable Long id,
			@AuthenticationPrincipal HospitalUserPrincipal principal) {
		return encounterService.getById(id, principal);
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasRole('DOCTOR')")
	public EncounterResponse update(@PathVariable Long id,
			@Valid @RequestBody EncounterUpdateRequest request,
			@AuthenticationPrincipal HospitalUserPrincipal principal) {
		return encounterService.update(id, request, principal);
	}
}