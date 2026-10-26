package com.smart_hospital_ai.demo.controller;

import java.util.List;
import com.smart_hospital_ai.demo.dto.LabOrderCreateRequest;
import com.smart_hospital_ai.demo.dto.LabOrderResponse;
import com.smart_hospital_ai.demo.dto.LabResultCreateRequest;
import com.smart_hospital_ai.demo.dto.LabResultResponse;
import com.smart_hospital_ai.demo.dto.PageResponse;
import com.smart_hospital_ai.demo.entity.LabOrderStatus;
import com.smart_hospital_ai.demo.security.HospitalUserPrincipal;
import com.smart_hospital_ai.demo.service.LabOrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class LaboratoryController {

	private static final int MAX_PAGE_SIZE = 100;
	private final LabOrderService labOrderService;

	public LaboratoryController(LabOrderService labOrderService) {
		this.labOrderService = labOrderService;
	}

	@PostMapping("/encounters/{encounterId}/lab-orders")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasRole('DOCTOR')")
	public LabOrderResponse createOrder(@PathVariable Long encounterId,
			@Valid @RequestBody LabOrderCreateRequest request,
			@AuthenticationPrincipal HospitalUserPrincipal principal) {
		return labOrderService.createOrder(encounterId, request, principal);
	}

	@GetMapping("/lab-orders/worklist")
	@PreAuthorize("hasRole('LAB_TECHNICIAN')")
	public PageResponse<LabOrderResponse> worklist(@RequestParam(required = false) LabOrderStatus status,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			@AuthenticationPrincipal HospitalUserPrincipal principal) {
		return PageResponse.from(labOrderService.worklist(status, page, size, principal));
	}

	@GetMapping("/encounters/{encounterId}/lab-orders")
	@PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'PATIENT')")
	public List<LabOrderResponse> getOrdersForEncounter(@PathVariable Long encounterId,
			@AuthenticationPrincipal HospitalUserPrincipal principal) {
		return labOrderService.getForEncounter(encounterId, principal);
	}

	@PostMapping("/lab-orders/{orderId}/results")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasRole('LAB_TECHNICIAN')")
	public LabResultResponse enterResult(@PathVariable Long orderId,
			@Valid @RequestBody LabResultCreateRequest request,
			@AuthenticationPrincipal HospitalUserPrincipal principal) {
		return labOrderService.enterResult(orderId, request, principal);
	}

	@GetMapping("/lab-orders/{orderId}/results")
	@PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'PATIENT', 'LAB_TECHNICIAN')")
	public List<LabResultResponse> getResults(@PathVariable Long orderId,
			@AuthenticationPrincipal HospitalUserPrincipal principal) {
		return labOrderService.getResults(orderId, principal);
	}

	@PostMapping("/lab-orders/{orderId}/results/{resultId}/verify")
	@PreAuthorize("hasRole('DOCTOR')")
	public LabResultResponse verifyResult(@PathVariable Long orderId, @PathVariable Long resultId,
			@AuthenticationPrincipal HospitalUserPrincipal principal) {
		return labOrderService.verifyResult(orderId, resultId, principal);
	}

	@GetMapping("/encounters/{encounterId}/verified-lab-results")
	@PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'PATIENT')")
	public List<LabResultResponse> getVerifiedResults(@PathVariable Long encounterId,
			@AuthenticationPrincipal HospitalUserPrincipal principal) {
		return labOrderService.getVerifiedResultsForEncounter(encounterId, principal);
	}
}