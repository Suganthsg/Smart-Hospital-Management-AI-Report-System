package com.smart_hospital_ai.demo.controller;

import com.smart_hospital_ai.demo.dto.DoctorAvailabilityRequest;
import com.smart_hospital_ai.demo.dto.DoctorAvailabilityResponse;
import com.smart_hospital_ai.demo.dto.DoctorCreateRequest;
import com.smart_hospital_ai.demo.dto.DoctorResponse;
import com.smart_hospital_ai.demo.dto.DoctorUpdateRequest;
import com.smart_hospital_ai.demo.dto.PageResponse;
import com.smart_hospital_ai.demo.security.HospitalUserPrincipal;
import com.smart_hospital_ai.demo.service.DoctorAvailabilityService;
import com.smart_hospital_ai.demo.service.DoctorService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/v1/doctors")
public class DoctorController {

	private final DoctorService doctorService;
	private final DoctorAvailabilityService availabilityService;

	public DoctorController(DoctorService doctorService, DoctorAvailabilityService availabilityService) {
		this.doctorService = doctorService;
		this.availabilityService = availabilityService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasRole('ADMIN')")
	public DoctorResponse create(@Valid @RequestBody DoctorCreateRequest request) {
		return doctorService.create(request);
	}

	@GetMapping
	public PageResponse<DoctorResponse> search(@RequestParam(required = false) String q,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return PageResponse.from(doctorService.search(q, page, size));
	}

	@GetMapping("/{id}")
	public DoctorResponse getById(@PathVariable Long id) {
		return doctorService.getById(id);
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	public DoctorResponse update(@PathVariable Long id, @Valid @RequestBody DoctorUpdateRequest request) {
		return doctorService.update(id, request);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize("hasRole('ADMIN')")
	public void delete(@PathVariable Long id) {
		doctorService.delete(id);
	}

	@GetMapping("/{id}/availability")
	public List<DoctorAvailabilityResponse> availability(@PathVariable Long id) {
		return availabilityService.getForDoctor(id);
	}

	@PostMapping("/{id}/availability")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR')")
	public DoctorAvailabilityResponse addAvailability(@PathVariable Long id,
			@Valid @RequestBody DoctorAvailabilityRequest request,
			@AuthenticationPrincipal HospitalUserPrincipal principal) {
		return availabilityService.add(id, request, principal);
	}

	@DeleteMapping("/{doctorId}/availability/{availabilityId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR')")
	public void removeAvailability(@PathVariable Long doctorId, @PathVariable Long availabilityId,
			@AuthenticationPrincipal HospitalUserPrincipal principal) {
		availabilityService.remove(doctorId, availabilityId, principal);
	}
}