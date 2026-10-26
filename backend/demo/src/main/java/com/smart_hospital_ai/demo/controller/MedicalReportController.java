package com.smart_hospital_ai.demo.controller;

import java.util.List;
import com.smart_hospital_ai.demo.dto.ReportHistoryResponse;
import com.smart_hospital_ai.demo.dto.ReportResponse;
import com.smart_hospital_ai.demo.dto.ReportReviewRequest;
import com.smart_hospital_ai.demo.dto.ReportUpdateRequest;
import com.smart_hospital_ai.demo.security.HospitalUserPrincipal;
import com.smart_hospital_ai.demo.service.MedicalReportService;
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
public class MedicalReportController {

	private final MedicalReportService medicalReportService;

	public MedicalReportController(MedicalReportService medicalReportService) {
		this.medicalReportService = medicalReportService;
	}

	@PostMapping("/encounters/{encounterId}/reports/generate")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasRole('DOCTOR')")
	public ReportResponse generateDraft(@PathVariable Long encounterId,
			@AuthenticationPrincipal HospitalUserPrincipal principal) {
		return medicalReportService.generateDraft(encounterId, principal);
	}

	@GetMapping("/encounters/{encounterId}/reports")
	@PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'PATIENT')")
	public List<ReportResponse> getForEncounter(@PathVariable Long encounterId,
			@AuthenticationPrincipal HospitalUserPrincipal principal) {
		return medicalReportService.getForEncounter(encounterId, principal);
	}

	@GetMapping("/reports/{reportId}")
	@PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'PATIENT')")
	public ReportResponse getById(@PathVariable Long reportId,
			@AuthenticationPrincipal HospitalUserPrincipal principal) {
		return medicalReportService.getById(reportId, principal);
	}

	@GetMapping("/reports/{reportId}/history")
	@PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR')")
	public List<ReportHistoryResponse> getHistory(@PathVariable Long reportId,
			@AuthenticationPrincipal HospitalUserPrincipal principal) {
		return medicalReportService.getHistory(reportId, principal);
	}

	@PutMapping("/reports/{reportId}/draft")
	@PreAuthorize("hasRole('DOCTOR')")
	public ReportResponse editDraft(@PathVariable Long reportId,
			@Valid @RequestBody ReportUpdateRequest request,
			@AuthenticationPrincipal HospitalUserPrincipal principal) {
		return medicalReportService.editDraft(reportId, request, principal);
	}

	@PostMapping("/reports/{reportId}/submit")
	@PreAuthorize("hasRole('DOCTOR')")
	public ReportResponse submitForReview(@PathVariable Long reportId,
			@AuthenticationPrincipal HospitalUserPrincipal principal) {
		return medicalReportService.submitForReview(reportId, principal);
	}

	@PostMapping("/reports/{reportId}/approve")
	@PreAuthorize("hasRole('DOCTOR')")
	public ReportResponse approve(@PathVariable Long reportId,
			@Valid @RequestBody ReportReviewRequest request,
			@AuthenticationPrincipal HospitalUserPrincipal principal) {
		return medicalReportService.approve(reportId, request, principal);
	}

	@PostMapping("/reports/{reportId}/reject")
	@PreAuthorize("hasRole('DOCTOR')")
	public ReportResponse reject(@PathVariable Long reportId,
			@Valid @RequestBody ReportReviewRequest request,
			@AuthenticationPrincipal HospitalUserPrincipal principal) {
		return medicalReportService.reject(reportId, request, principal);
	}
}