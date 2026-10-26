package com.smart_hospital_ai.demo.controller;

import com.smart_hospital_ai.demo.dto.AppointmentCreateRequest;
import com.smart_hospital_ai.demo.dto.AppointmentResponse;
import com.smart_hospital_ai.demo.dto.AppointmentRescheduleRequest;
import com.smart_hospital_ai.demo.dto.PageResponse;
import com.smart_hospital_ai.demo.entity.AppointmentStatus;
import com.smart_hospital_ai.demo.security.HospitalUserPrincipal;
import com.smart_hospital_ai.demo.service.AppointmentService;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
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
@RequestMapping("/api/v1/appointments")
@PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'RECEPTIONIST', 'PATIENT')")
public class AppointmentController {

	private final AppointmentService appointmentService;

	public AppointmentController(AppointmentService appointmentService) {
		this.appointmentService = appointmentService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public AppointmentResponse create(@Valid @RequestBody AppointmentCreateRequest request,
			@AuthenticationPrincipal HospitalUserPrincipal principal) {
		return appointmentService.create(request, principal);
	}

	@GetMapping
	public PageResponse<AppointmentResponse> search(
			@RequestParam(required = false) AppointmentStatus status,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime fromAt,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime toAt,
			@RequestParam(required = false) Long patientId,
			@RequestParam(required = false) Long doctorId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			@AuthenticationPrincipal HospitalUserPrincipal principal) {
		Page<AppointmentResponse> results = appointmentService.search(status, fromAt, toAt,
			patientId, doctorId, page, size, principal);
		return PageResponse.from(results);
	}

	@GetMapping("/{id}")
	public AppointmentResponse getById(@PathVariable Long id,
			@AuthenticationPrincipal HospitalUserPrincipal principal) {
		return appointmentService.getById(id, principal);
	}

	@PutMapping("/{id}/reschedule")
	public AppointmentResponse reschedule(@PathVariable Long id,
			@Valid @RequestBody AppointmentRescheduleRequest request,
			@AuthenticationPrincipal HospitalUserPrincipal principal) {
		return appointmentService.reschedule(id, request, principal);
	}

	@PatchMapping("/{id}/cancel")
	public AppointmentResponse cancel(@PathVariable Long id,
			@AuthenticationPrincipal HospitalUserPrincipal principal) {
		return appointmentService.cancel(id, principal);
	}
}