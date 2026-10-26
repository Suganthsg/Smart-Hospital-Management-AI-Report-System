package com.smart_hospital_ai.demo.service;

import java.util.List;
import com.smart_hospital_ai.demo.dto.ReportHistoryResponse;
import com.smart_hospital_ai.demo.dto.ReportResponse;
import com.smart_hospital_ai.demo.dto.ReportReviewRequest;
import com.smart_hospital_ai.demo.dto.ReportUpdateRequest;
import com.smart_hospital_ai.demo.dto.VerifiedLabResultInput;
import com.smart_hospital_ai.demo.entity.Encounter;
import com.smart_hospital_ai.demo.entity.EncounterStatus;
import com.smart_hospital_ai.demo.entity.LabResult;
import com.smart_hospital_ai.demo.entity.LabResultStatus;
import com.smart_hospital_ai.demo.entity.MedicalReport;
import com.smart_hospital_ai.demo.entity.MedicalReportHistory;
import com.smart_hospital_ai.demo.entity.MedicalReportHistoryEventType;
import com.smart_hospital_ai.demo.entity.MedicalReportStatus;
import com.smart_hospital_ai.demo.entity.ReportGenerationMode;
import com.smart_hospital_ai.demo.entity.UserAccount;
import com.smart_hospital_ai.demo.entity.UserRole;
import com.smart_hospital_ai.demo.exception.ApiException;
import com.smart_hospital_ai.demo.repository.EncounterRepository;
import com.smart_hospital_ai.demo.repository.LabResultRepository;
import com.smart_hospital_ai.demo.repository.MedicalReportHistoryRepository;
import com.smart_hospital_ai.demo.repository.MedicalReportRepository;
import com.smart_hospital_ai.demo.repository.UserRepository;
import com.smart_hospital_ai.demo.security.HospitalUserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MedicalReportService {

	private final MedicalReportRepository reportRepository;
	private final MedicalReportHistoryRepository historyRepository;
	private final EncounterRepository encounterRepository;
	private final LabResultRepository labResultRepository;
	private final UserRepository userRepository;
	private final ReportGenerationService generationService;

	public MedicalReportService(MedicalReportRepository reportRepository,
			MedicalReportHistoryRepository historyRepository, EncounterRepository encounterRepository,
			LabResultRepository labResultRepository, UserRepository userRepository,
			ReportGenerationService generationService) {
		this.reportRepository = reportRepository;
		this.historyRepository = historyRepository;
		this.encounterRepository = encounterRepository;
		this.labResultRepository = labResultRepository;
		this.userRepository = userRepository;
		this.generationService = generationService;
	}

	@Transactional
	public ReportResponse generateDraft(Long encounterId, HospitalUserPrincipal principal) {
		Encounter encounter = encounterRepository.findByIdForUpdate(encounterId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Encounter not found"));
		assertAssignedClinician(encounter, principal);
		if (encounter.getStatus() != EncounterStatus.FINALIZED) {
			throw new ApiException(HttpStatus.CONFLICT, "Reports require a finalized encounter");
		}

		List<VerifiedLabResultInput> verifiedResults = labResultRepository.findCurrentByEncounterAndStatus(
			encounterId, LabResultStatus.VERIFIED).stream().map(MedicalReportService::toGenerationInput).toList();
		ReportGenerationInput input = new ReportGenerationInput(encounter.getEncounterDate(),
			encounter.getDiagnosis(), encounter.getClinicalNotes(), verifiedResults);
		GeneratedReportContent generated = generationService.generate(input);
		if (generated.generationMode() != ReportGenerationMode.MOCK) {
			throw new IllegalStateException("External report generation is disabled");
		}

		UserAccount author = userRepository.getReferenceById(principal.getUserId());
		MedicalReport report = reportRepository.save(new MedicalReport(encounter, author,
			generated.content(), generated.generationMode()));
		recordHistory(report, author, MedicalReportHistoryEventType.GENERATED, null);
		return toResponse(report);
	}

	@Transactional(readOnly = true)
	public ReportResponse getById(Long reportId, HospitalUserPrincipal principal) {
		MedicalReport report = findReport(reportId);
		assertCanRead(report, principal);
		return toResponse(report);
	}

	@Transactional(readOnly = true)
	public List<ReportResponse> getForEncounter(Long encounterId, HospitalUserPrincipal principal) {
		Encounter encounter = encounterRepository.findById(encounterId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Encounter not found"));
		assertCanReadEncounter(encounter, principal);
		return reportRepository.findByEncounterIdOrderByCreatedAtDescIdDesc(encounterId).stream()
			.filter(report -> principal.getRole() != UserRole.PATIENT
				|| report.getStatus() == MedicalReportStatus.APPROVED)
			.map(MedicalReportService::toResponse).toList();
	}

	@Transactional(readOnly = true)
	public List<ReportHistoryResponse> getHistory(Long reportId, HospitalUserPrincipal principal) {
		MedicalReport report = findReport(reportId);
		assertCanRead(report, principal);
		if (principal.getRole() == UserRole.PATIENT) {
			throw new AccessDeniedException("Report history is restricted to clinicians");
		}
		return historyRepository.findByReportIdOrderByOccurredAtAscIdAsc(reportId).stream()
			.map(MedicalReportService::toHistoryResponse).toList();
	}

	@Transactional
	public ReportResponse editDraft(Long reportId, ReportUpdateRequest request,
			HospitalUserPrincipal principal) {
		MedicalReport report = reportRepository.findByIdForUpdate(reportId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Report not found"));
		assertAssignedClinician(report.getEncounter(), principal);
		if (!report.getAuthor().getId().equals(principal.getUserId())) {
			throw new AccessDeniedException("Only the report author may edit this draft");
		}
		if (report.getStatus() != MedicalReportStatus.DRAFT && report.getStatus() != MedicalReportStatus.REJECTED) {
			throw new ApiException(HttpStatus.CONFLICT, "Only draft or rejected reports may be edited");
		}
		report.edit(request.content().trim());
		recordHistory(report, userRepository.getReferenceById(principal.getUserId()),
			MedicalReportHistoryEventType.EDITED, null);
		return toResponse(report);
	}

	@Transactional
	public ReportResponse submitForReview(Long reportId, HospitalUserPrincipal principal) {
		MedicalReport report = reportRepository.findByIdForUpdate(reportId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Report not found"));
		assertAssignedClinician(report.getEncounter(), principal);
		if (!report.getAuthor().getId().equals(principal.getUserId())) {
			throw new AccessDeniedException("Only the report author may submit this draft");
		}
		if (report.getStatus() != MedicalReportStatus.DRAFT) {
			throw new ApiException(HttpStatus.CONFLICT, "Only a draft report can be submitted for review");
		}
		report.submit();
		recordHistory(report, userRepository.getReferenceById(principal.getUserId()),
			MedicalReportHistoryEventType.SUBMITTED, null);
		return toResponse(report);
	}

	@Transactional
	public ReportResponse approve(Long reportId, ReportReviewRequest request,
			HospitalUserPrincipal principal) {
		MedicalReport report = reportRepository.findByIdForUpdate(reportId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Report not found"));
		assertAssignedClinician(report.getEncounter(), principal);
		if (report.getStatus() != MedicalReportStatus.IN_REVIEW) {
			throw new ApiException(HttpStatus.CONFLICT, "Only reports submitted for review can be approved");
		}
		UserAccount reviewer = userRepository.getReferenceById(principal.getUserId());
		String notes = blankToNull(request.notes());
		report.approve(reviewer, notes);
		recordHistory(report, reviewer, MedicalReportHistoryEventType.APPROVED, notes);
		return toResponse(report);
	}

	@Transactional
	public ReportResponse reject(Long reportId, ReportReviewRequest request,
			HospitalUserPrincipal principal) {
		MedicalReport report = reportRepository.findByIdForUpdate(reportId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Report not found"));
		assertAssignedClinician(report.getEncounter(), principal);
		if (report.getStatus() != MedicalReportStatus.IN_REVIEW) {
			throw new ApiException(HttpStatus.CONFLICT, "Only reports submitted for review can be rejected");
		}
		String notes = blankToNull(request.notes());
		if (notes == null) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Reviewer notes are required when rejecting a report");
		}
		UserAccount reviewer = userRepository.getReferenceById(principal.getUserId());
		report.reject(reviewer, notes);
		recordHistory(report, reviewer, MedicalReportHistoryEventType.REJECTED, notes);
		return toResponse(report);
	}

	private MedicalReport findReport(Long reportId) {
		return reportRepository.findById(reportId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Report not found"));
	}

	private void recordHistory(MedicalReport report, UserAccount actor,
			MedicalReportHistoryEventType eventType, String note) {
		historyRepository.save(new MedicalReportHistory(report, actor, eventType, note));
	}

	private static VerifiedLabResultInput toGenerationInput(LabResult result) {
		return new VerifiedLabResultInput(result.getLabOrder().getTestName(), result.getResultValue(),
			result.getUnits(), result.getReferenceRange(), result.getVerifiedAt());
	}

	private static void assertAssignedClinician(Encounter encounter, HospitalUserPrincipal principal) {
		if (principal.getRole() != UserRole.DOCTOR
				|| !encounter.getDoctor().getUser().getId().equals(principal.getUserId())
				|| encounter.getPatient().getAssignedDoctor() == null
				|| !encounter.getPatient().getAssignedDoctor().getId().equals(encounter.getDoctor().getId())) {
			throw new AccessDeniedException("Only the assigned doctor may manage this report");
		}
	}

	private static void assertCanReadEncounter(Encounter encounter, HospitalUserPrincipal principal) {
		if (principal.getRole() == UserRole.ADMIN || isPatientOwner(encounter, principal)) {
			return;
		}
		assertAssignedClinician(encounter, principal);
	}

	private static void assertCanRead(MedicalReport report, HospitalUserPrincipal principal) {
		assertCanReadEncounter(report.getEncounter(), principal);
		if (isPatientOwner(report.getEncounter(), principal)
				&& report.getStatus() != MedicalReportStatus.APPROVED) {
			throw new AccessDeniedException("Only approved reports are available to patients");
		}
	}

	private static boolean isPatientOwner(Encounter encounter, HospitalUserPrincipal principal) {
		return principal.getRole() == UserRole.PATIENT
			&& encounter.getPatient().getUser().getId().equals(principal.getUserId());
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private static ReportResponse toResponse(MedicalReport report) {
		return new ReportResponse(report.getId(), report.getEncounter().getId(), report.getStatus(),
			report.getGenerationMode(), report.getContent(), report.getAuthor().getId(),
			report.getReviewer() == null ? null : report.getReviewer().getId(), report.getCreatedAt(),
			report.getUpdatedAt(), report.getSubmittedAt(), report.getReviewedAt(), report.getReviewerNotes());
	}

	private static ReportHistoryResponse toHistoryResponse(MedicalReportHistory history) {
		return new ReportHistoryResponse(history.getId(), history.getActor().getId(), history.getEventType(),
			history.getReportStatus(), history.getContentSnapshot(), history.getEventNote(), history.getOccurredAt());
	}
}