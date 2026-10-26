package com.smart_hospital_ai.demo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import com.smart_hospital_ai.demo.dto.ReportReviewRequest;
import com.smart_hospital_ai.demo.dto.ReportUpdateRequest;
import com.smart_hospital_ai.demo.dto.VerifiedLabResultInput;
import com.smart_hospital_ai.demo.entity.Doctor;
import com.smart_hospital_ai.demo.entity.Encounter;
import com.smart_hospital_ai.demo.entity.EncounterStatus;
import com.smart_hospital_ai.demo.entity.LabResultStatus;
import com.smart_hospital_ai.demo.entity.MedicalReport;
import com.smart_hospital_ai.demo.entity.MedicalReportHistory;
import com.smart_hospital_ai.demo.entity.MedicalReportHistoryEventType;
import com.smart_hospital_ai.demo.entity.MedicalReportStatus;
import com.smart_hospital_ai.demo.entity.Patient;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class MedicalReportServiceTest {

	private final MedicalReportRepository reportRepository = mock(MedicalReportRepository.class);
	private final MedicalReportHistoryRepository historyRepository = mock(MedicalReportHistoryRepository.class);
	private final EncounterRepository encounterRepository = mock(EncounterRepository.class);
	private final LabResultRepository labResultRepository = mock(LabResultRepository.class);
	private final UserRepository userRepository = mock(UserRepository.class);
	private final ReportGenerationService generationService = mock(ReportGenerationService.class);
	private final MedicalReportService service = new MedicalReportService(reportRepository, historyRepository,
		encounterRepository, labResultRepository, userRepository, generationService);
	private final Encounter encounter = mock(Encounter.class);
	private final Doctor doctor = mock(Doctor.class);
	private final Patient patient = mock(Patient.class);
	private final UserAccount user = mock(UserAccount.class);
	private final HospitalUserPrincipal clinician = mock(HospitalUserPrincipal.class);

	@BeforeEach
	void setUp() {
		when(clinician.getRole()).thenReturn(UserRole.DOCTOR);
		when(clinician.getUserId()).thenReturn(5L);
		when(encounter.getId()).thenReturn(11L);
		when(encounter.getDoctor()).thenReturn(doctor);
		when(encounter.getPatient()).thenReturn(patient);
		when(encounter.getStatus()).thenReturn(EncounterStatus.FINALIZED);
		when(encounter.getEncounterDate()).thenReturn(LocalDate.now());
		when(encounter.getDiagnosis()).thenReturn("Recorded diagnosis");
		when(encounter.getClinicalNotes()).thenReturn("Recorded notes");
		when(doctor.getUser()).thenReturn(user);
		when(user.getId()).thenReturn(5L);
		when(patient.getAssignedDoctor()).thenReturn(doctor);
		when(encounterRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(encounter));
		when(labResultRepository.findCurrentByEncounterAndStatus(11L, LabResultStatus.VERIFIED))
			.thenReturn(List.of());
		when(userRepository.getReferenceById(5L)).thenReturn(user);
		when(reportRepository.save(any(MedicalReport.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(historyRepository.save(any(MedicalReportHistory.class)))
			.thenAnswer(invocation -> invocation.getArgument(0));
	}

	@Test
	void generatedReportIsOnlyAMockDraftAndUsesVerifiedInputs() {
		when(labResultRepository.findCurrentByEncounterAndStatus(11L, LabResultStatus.VERIFIED))
			.thenReturn(List.of());
		when(generationService.generate(any(ReportGenerationInput.class)))
			.thenReturn(new GeneratedReportContent("Recorded notes", ReportGenerationMode.MOCK));

		var response = service.generateDraft(11L, clinician);

		assertEquals(MedicalReportStatus.DRAFT, response.status());
		assertEquals(ReportGenerationMode.MOCK, response.generationMode());
		verify(generationService).generate(any(ReportGenerationInput.class));
		verify(historyRepository).save(any(MedicalReportHistory.class));
	}

	@Test
	void refusesGenerationFromDraftEncounter() {
		when(encounter.getStatus()).thenReturn(EncounterStatus.DRAFT);

		assertThrows(ApiException.class, () -> service.generateDraft(11L, clinician));
		verify(generationService, never()).generate(any(ReportGenerationInput.class));
	}

	@Test
	void onlyAssignedDoctorCanSubmitOrApprove() {
		when(patient.getAssignedDoctor()).thenReturn(null);

		assertThrows(AccessDeniedException.class, () -> service.generateDraft(11L, clinician));
		verify(reportRepository, never()).save(any(MedicalReport.class));
	}

	@Test
	void reportApprovalRequiresReviewState() {
		MedicalReport report = mock(MedicalReport.class);
		when(reportRepository.findByIdForUpdate(15L)).thenReturn(Optional.of(report));
		when(report.getEncounter()).thenReturn(encounter);
		when(report.getStatus()).thenReturn(MedicalReportStatus.DRAFT);

		assertThrows(ApiException.class, () -> service.approve(15L, new ReportReviewRequest(null), clinician));
		verify(report, never()).approve(any(UserAccount.class), any());
	}

	@Test
	void patientCannotReadUnapprovedReport() {
		MedicalReport report = mock(MedicalReport.class);
		when(reportRepository.findById(15L)).thenReturn(Optional.of(report));
		when(report.getEncounter()).thenReturn(encounter);
		when(report.getStatus()).thenReturn(MedicalReportStatus.DRAFT);
		when(patient.getUser()).thenReturn(user);
		when(user.getId()).thenReturn(9L);
		HospitalUserPrincipal patientPrincipal = mock(HospitalUserPrincipal.class);
		when(patientPrincipal.getRole()).thenReturn(UserRole.PATIENT);
		when(patientPrincipal.getUserId()).thenReturn(9L);

		assertThrows(AccessDeniedException.class, () -> service.getById(15L, patientPrincipal));
	}
}