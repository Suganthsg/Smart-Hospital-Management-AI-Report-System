package com.smart_hospital_ai.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import com.smart_hospital_ai.demo.dto.DoctorCreateRequest;
import com.smart_hospital_ai.demo.dto.EncounterCreateRequest;
import com.smart_hospital_ai.demo.dto.EncounterUpdateRequest;
import com.smart_hospital_ai.demo.dto.LabOrderCreateRequest;
import com.smart_hospital_ai.demo.dto.LabResultCreateRequest;
import com.smart_hospital_ai.demo.dto.PatientRegistrationRequest;
import com.smart_hospital_ai.demo.dto.ReportReviewRequest;
import com.smart_hospital_ai.demo.entity.EncounterStatus;
import com.smart_hospital_ai.demo.entity.MedicalReportHistoryEventType;
import com.smart_hospital_ai.demo.entity.MedicalReportStatus;
import com.smart_hospital_ai.demo.entity.UserAccount;
import com.smart_hospital_ai.demo.entity.UserRole;
import com.smart_hospital_ai.demo.repository.UserRepository;
import com.smart_hospital_ai.demo.security.HospitalUserPrincipal;
import com.smart_hospital_ai.demo.service.DoctorService;
import com.smart_hospital_ai.demo.service.EncounterService;
import com.smart_hospital_ai.demo.service.LabOrderService;
import com.smart_hospital_ai.demo.service.MedicalReportService;
import com.smart_hospital_ai.demo.service.PatientService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ExtendWith(PostgresEnvironmentCondition.class)
@Transactional
class MedicalReportPostgresIntegrationTest {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private DoctorService doctorService;

	@Autowired
	private PatientService patientService;

	@Autowired
	private EncounterService encounterService;

	@Autowired
	private LabOrderService labOrderService;

	@Autowired
	private MedicalReportService medicalReportService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Test
	void flywayV7AndReportHistoryTablesArePresent() {
		Integer successfulV7 = jdbcTemplate.queryForObject("""
			select count(*) from flyway_schema_history where version = '7' and success = true
			""", Integer.class);
		Integer reportTables = jdbcTemplate.queryForObject("""
			select count(*) from information_schema.tables
			where table_schema = current_schema()
			and table_name in ('medical_reports', 'medical_report_history')
			""", Integer.class);

		assertEquals(1, successfulV7);
		assertEquals(2, reportTables);
	}

	@Test
	void generatesOnlyFromRecordedAndVerifiedInputsThenRequiresReview() {
		Fixture fixture = createFinalizedEncounter();
		String technicianEmail = uniqueEmail("labtech");
		UserAccount technicianAccount = userRepository.save(new UserAccount(technicianEmail,
			passwordEncoder.encode("Report-test-password"), UserRole.LAB_TECHNICIAN));
		HospitalUserPrincipal technician = new HospitalUserPrincipal(technicianAccount);

		var verifiedOrder = labOrderService.createOrder(fixture.encounterId(),
			new LabOrderCreateRequest("Sodium"), fixture.doctor());
		var verifiedResult = labOrderService.enterResult(verifiedOrder.id(),
			new LabResultCreateRequest("140", "mmol/L", "135-145", null), technician);
		labOrderService.verifyResult(verifiedOrder.id(), verifiedResult.id(), fixture.doctor());

		var pendingOrder = labOrderService.createOrder(fixture.encounterId(),
			new LabOrderCreateRequest("Pending test"), fixture.doctor());
		labOrderService.enterResult(pendingOrder.id(),
			new LabResultCreateRequest("NEVER_INCLUDE_THIS", null, null, null), technician);

		var draft = medicalReportService.generateDraft(fixture.encounterId(), fixture.doctor());
		assertEquals(MedicalReportStatus.DRAFT, draft.status());
		assertTrue(draft.content().contains("Recorded encounter diagnosis"));
		assertTrue(draft.content().contains("Recorded encounter notes"));
		assertTrue(draft.content().contains("Sodium: 140 mmol/L"));
		assertTrue(draft.content().contains("Recorded reference range: 135-145"));
		assertFalse(draft.content().contains("Pending test"));
		assertFalse(draft.content().contains("NEVER_INCLUDE_THIS"));
		assertThrows(AccessDeniedException.class,
			() -> medicalReportService.getById(draft.id(), fixture.patient()));

		var submitted = medicalReportService.submitForReview(draft.id(), fixture.doctor());
		assertEquals(MedicalReportStatus.IN_REVIEW, submitted.status());
		var approved = medicalReportService.approve(draft.id(), new ReportReviewRequest("Clinician reviewed"),
			fixture.doctor());
		assertEquals(MedicalReportStatus.APPROVED, approved.status());
		assertEquals(fixture.doctor().getUserId(), approved.reviewerUserId());
		assertEquals(draft.id(), medicalReportService.getById(draft.id(), fixture.patient()).id());

		List<MedicalReportHistoryEventType> events = medicalReportService.getHistory(draft.id(), fixture.doctor())
			.stream().map(history -> history.eventType()).toList();
		assertEquals(List.of(MedicalReportHistoryEventType.GENERATED, MedicalReportHistoryEventType.SUBMITTED,
			MedicalReportHistoryEventType.APPROVED), events);
	}

	private Fixture createFinalizedEncounter() {
		String doctorEmail = uniqueEmail("doctor");
		var doctor = doctorService.create(new DoctorCreateRequest(doctorEmail,
			"Report-test-password-42", "LIC-" + UUID.randomUUID(), "Report", "Doctor", "General Practice", null));
		String patientEmail = uniqueEmail("patient");
		var patient = patientService.register(new PatientRegistrationRequest(patientEmail,
			"Report-test-password-42", "Report", "Patient", null, null));
		patientService.assignDoctor(patient.id(), doctor.id());
		UserAccount doctorAccount = userRepository.findByEmailIgnoreCase(doctorEmail).orElseThrow();
		UserAccount patientAccount = userRepository.findByEmailIgnoreCase(patientEmail).orElseThrow();
		HospitalUserPrincipal doctorPrincipal = new HospitalUserPrincipal(doctorAccount);
		var encounter = encounterService.create(new EncounterCreateRequest(patient.id(), null,
			LocalDate.now(), "Recorded encounter notes", "Recorded encounter diagnosis"), doctorPrincipal);
		encounterService.update(encounter.id(), new EncounterUpdateRequest(LocalDate.now(),
			"Recorded encounter notes", "Recorded encounter diagnosis", EncounterStatus.FINALIZED), doctorPrincipal);
		return new Fixture(encounter.id(), doctorPrincipal, new HospitalUserPrincipal(patientAccount));
	}

	private static String uniqueEmail(String prefix) {
		return prefix + "+report-" + UUID.randomUUID() + "@example.test";
	}

	private record Fixture(Long encounterId, HospitalUserPrincipal doctor, HospitalUserPrincipal patient) {
	}
}