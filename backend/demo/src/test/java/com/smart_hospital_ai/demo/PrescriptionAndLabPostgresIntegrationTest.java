package com.smart_hospital_ai.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import com.smart_hospital_ai.demo.dto.DoctorCreateRequest;
import com.smart_hospital_ai.demo.dto.EncounterCreateRequest;
import com.smart_hospital_ai.demo.dto.EncounterUpdateRequest;
import com.smart_hospital_ai.demo.dto.LabOrderCreateRequest;
import com.smart_hospital_ai.demo.dto.LabResultCreateRequest;
import com.smart_hospital_ai.demo.dto.PatientRegistrationRequest;
import com.smart_hospital_ai.demo.dto.PrescriptionCreateRequest;
import com.smart_hospital_ai.demo.dto.PrescriptionItemRequest;
import com.smart_hospital_ai.demo.dto.PrescriptionUpdateRequest;
import com.smart_hospital_ai.demo.entity.EncounterStatus;
import com.smart_hospital_ai.demo.entity.LabResultStatus;
import com.smart_hospital_ai.demo.entity.PrescriptionHistoryEventType;
import com.smart_hospital_ai.demo.entity.PrescriptionStatus;
import com.smart_hospital_ai.demo.entity.UserAccount;
import com.smart_hospital_ai.demo.entity.UserRole;
import com.smart_hospital_ai.demo.repository.UserRepository;
import com.smart_hospital_ai.demo.security.HospitalUserPrincipal;
import com.smart_hospital_ai.demo.service.DoctorService;
import com.smart_hospital_ai.demo.service.EncounterService;
import com.smart_hospital_ai.demo.service.LabOrderService;
import com.smart_hospital_ai.demo.service.PatientService;
import com.smart_hospital_ai.demo.service.PrescriptionService;
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
class PrescriptionAndLabPostgresIntegrationTest {

	@Autowired
	private DoctorService doctorService;

	@Autowired
	private PatientService patientService;

	@Autowired
	private EncounterService encounterService;

	@Autowired
	private PrescriptionService prescriptionService;

	@Autowired
	private LabOrderService labOrderService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void prescriptionAndLaboratoryMigrationsAreApplied() {
		Integer successfulClinicalMigrations = jdbcTemplate.queryForObject("""
			select count(*) from flyway_schema_history
			where version in ('5', '6') and success = true
			""", Integer.class);
		Integer clinicalTables = jdbcTemplate.queryForObject("""
			select count(*) from information_schema.tables
			where table_schema = current_schema()
			and table_name in ('prescriptions', 'prescription_items', 'prescription_history',
				'prescription_history_items', 'lab_orders', 'lab_results')
			""", Integer.class);

		assertEquals(2, successfulClinicalMigrations);
		assertEquals(6, clinicalTables);
	}

	@Test
	void prescriptionSnapshotsSurviveUpdateAndFinalization() {
		Fixture fixture = createFinalizedEncounter();
		var created = prescriptionService.create(fixture.encounterId(),
			new PrescriptionCreateRequest(List.of(medicine("Medicine A", 3))), fixture.doctor());
		assertEquals(PrescriptionStatus.DRAFT, created.status());

		var updated = prescriptionService.update(created.id(),
			new PrescriptionUpdateRequest(List.of(medicine("Medicine B", 5))), fixture.doctor());
		assertEquals("Medicine B", updated.items().getFirst().medicineName());
		var finalized = prescriptionService.finalizePrescription(created.id(), fixture.doctor());
		assertEquals(PrescriptionStatus.FINALIZED, finalized.status());

		var history = prescriptionService.getHistory(created.id(), fixture.doctor());
		assertEquals(List.of(PrescriptionHistoryEventType.CREATED, PrescriptionHistoryEventType.UPDATED,
			PrescriptionHistoryEventType.FINALIZED), history.stream().map(event -> event.eventType()).toList());
		assertEquals("Medicine A", history.getFirst().items().getFirst().medicineName());
		assertEquals("Medicine B", history.get(1).items().getFirst().medicineName());
		assertThrows(RuntimeException.class, () -> prescriptionService.update(created.id(),
			new PrescriptionUpdateRequest(List.of(medicine("Medicine C", 1))), fixture.doctor()));
	}

	@Test
	void laboratoryResultVersionsAreVerifiedAndPatientSeesOnlyLatestVerifiedValue() {
		Fixture fixture = createFinalizedEncounter();
		String technicianEmail = uniqueEmail("technician");
		UserAccount technicianAccount = userRepository.save(new UserAccount(technicianEmail,
			passwordEncoder.encode("lab-test-password"), UserRole.LAB_TECHNICIAN));
		HospitalUserPrincipal technician = new HospitalUserPrincipal(technicianAccount);
		var order = labOrderService.createOrder(fixture.encounterId(),
			new LabOrderCreateRequest("Serum sodium"), fixture.doctor());
		assertEquals(1, labOrderService.worklist(null, 0, 20, technician).getTotalElements());

		var first = labOrderService.enterResult(order.id(),
			new LabResultCreateRequest("140", "mmol/L", "135-145", null), technician);
		assertEquals(1, first.resultVersion());
		var verifiedFirst = labOrderService.verifyResult(order.id(), first.id(), fixture.doctor());
		assertEquals(LabResultStatus.VERIFIED, verifiedFirst.status());

		var correction = labOrderService.enterResult(order.id(),
			new LabResultCreateRequest("141", "mmol/L", "135-145", "Corrected instrument entry"), technician);
		assertEquals(2, correction.resultVersion());
		assertEquals(LabResultStatus.ENTERED, correction.status());
		assertEquals(0, labOrderService.getVerifiedResultsForEncounter(
			fixture.encounterId(), fixture.doctor()).size());
		assertEquals(0, labOrderService.getResults(order.id(), fixture.patient()).size());

		labOrderService.verifyResult(order.id(), correction.id(), fixture.doctor());
		assertEquals(1, labOrderService.getResults(order.id(), fixture.patient()).size());
		assertEquals(1, labOrderService.getVerifiedResultsForEncounter(
			fixture.encounterId(), fixture.doctor()).size());
		assertThrows(AccessDeniedException.class, () -> labOrderService.enterResult(order.id(),
			new LabResultCreateRequest("142", null, null, null), fixture.doctor()));
	}

	private Fixture createFinalizedEncounter() {
		String doctorEmail = uniqueEmail("doctor");
		var doctor = doctorService.create(new DoctorCreateRequest(doctorEmail,
			"Encounter-test-password-42", "LIC-" + UUID.randomUUID(), "Test", "Doctor", "General Practice", null));
		String patientEmail = uniqueEmail("patient");
		var patient = patientService.register(new PatientRegistrationRequest(patientEmail,
			"Encounter-test-password-42", "Test", "Patient", null, null));
		patientService.assignDoctor(patient.id(), doctor.id());
		UserAccount doctorAccount = userRepository.findByEmailIgnoreCase(doctorEmail).orElseThrow();
		UserAccount patientAccount = userRepository.findByEmailIgnoreCase(patientEmail).orElseThrow();
		HospitalUserPrincipal doctorPrincipal = new HospitalUserPrincipal(doctorAccount);
		var encounter = encounterService.create(new EncounterCreateRequest(patient.id(), null,
			LocalDate.now(), "Clinical notes", "Diagnosis"), doctorPrincipal);
		encounterService.update(encounter.id(), new EncounterUpdateRequest(LocalDate.now(),
			"Clinical notes", "Diagnosis", EncounterStatus.FINALIZED), doctorPrincipal);
		return new Fixture(encounter.id(), doctorPrincipal, new HospitalUserPrincipal(patientAccount));
	}

	private static PrescriptionItemRequest medicine(String name, int quantity) {
		return new PrescriptionItemRequest(name, "5 mg", "oral", "once daily", "3 days", quantity, null);
	}

	private static String uniqueEmail(String prefix) {
		return prefix + "+clinical-" + UUID.randomUUID() + "@example.test";
	}

	private record Fixture(Long encounterId, HospitalUserPrincipal doctor, HospitalUserPrincipal patient) {
	}
}