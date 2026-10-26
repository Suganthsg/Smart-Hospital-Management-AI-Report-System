package com.smart_hospital_ai.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.smart_hospital_ai.demo.dto.DoctorCreateRequest;
import com.smart_hospital_ai.demo.dto.EncounterCreateRequest;
import com.smart_hospital_ai.demo.dto.EncounterUpdateRequest;
import com.smart_hospital_ai.demo.dto.PatientRegistrationRequest;
import com.smart_hospital_ai.demo.entity.EncounterStatus;
import com.smart_hospital_ai.demo.entity.UserAccount;
import com.smart_hospital_ai.demo.entity.UserRole;
import com.smart_hospital_ai.demo.repository.UserRepository;
import com.smart_hospital_ai.demo.security.HospitalUserPrincipal;
import com.smart_hospital_ai.demo.service.DoctorService;
import com.smart_hospital_ai.demo.service.EncounterService;
import com.smart_hospital_ai.demo.service.PatientService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;

@SpringBootTest
@ExtendWith(PostgresEnvironmentCondition.class)
class EncounterPostgresIntegrationTest {

	@Autowired
	private EncounterService encounterService;

	@Autowired
	private PatientService patientService;

	@Autowired
	private DoctorService doctorService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private final List<String> patientEmails = new ArrayList<>();
	private final List<String> doctorEmails = new ArrayList<>();

	@Test
	void persistsEncounterSearchesAndFinalizesAuthorizedRecord() {
		Fixture fixture = createFixture();
		var created = encounterService.create(new EncounterCreateRequest(fixture.patientId(), null,
			LocalDate.now(), "Clinical notes", "Diagnosis"), fixture.doctorPrincipal());

		assertEquals(EncounterStatus.DRAFT, created.status());
		assertNull(created.appointmentId());
		assertEquals(created.id(), encounterService.getById(created.id(), fixture.patientPrincipal()).id());
		assertEquals(1, encounterService.search(EncounterStatus.DRAFT, LocalDate.now(), LocalDate.now(),
			fixture.patientId(), null, 0, 10, fixture.patientPrincipal()).getTotalElements());

		var finalized = encounterService.update(created.id(), new EncounterUpdateRequest(LocalDate.now(),
			"Updated clinical notes", "Updated diagnosis", EncounterStatus.FINALIZED), fixture.doctorPrincipal());
		assertEquals(EncounterStatus.FINALIZED, finalized.status());
		assertEquals("Updated clinical notes", finalized.clinicalNotes());
	}

	@Test
	void searchesWithNoDateRangeAndInclusiveOptionalDateBounds() {
		Fixture fixture = createFixture();
		LocalDate oldest = LocalDate.now().minusDays(3);
		LocalDate middle = LocalDate.now().minusDays(2);
		LocalDate newest = LocalDate.now().minusDays(1);
		for (LocalDate encounterDate : List.of(oldest, middle, newest)) {
			encounterService.create(new EncounterCreateRequest(fixture.patientId(), null, encounterDate,
				"Notes for " + encounterDate, "Diagnosis"), fixture.doctorPrincipal());
		}

		assertEquals(3, encounterService.search(null, null, null, fixture.patientId(), null,
			0, 10, fixture.patientPrincipal()).getTotalElements());
		assertEquals(2, encounterService.search(null, middle, newest, fixture.patientId(), null,
			0, 10, fixture.patientPrincipal()).getTotalElements());
		assertEquals(2, encounterService.search(null, middle, null, fixture.patientId(), null,
			0, 10, fixture.patientPrincipal()).getTotalElements());
		assertEquals(2, encounterService.search(null, null, middle, fixture.patientId(), null,
			0, 10, fixture.patientPrincipal()).getTotalElements());
	}

	@Test
	void linksOptionalAppointmentAndRejectsAccessFromAnotherDoctor() {
		Fixture fixture = createFixture();
		OffsetDateTime appointmentStart = LocalDate.now().atTime(9, 0).atOffset(ZoneOffset.UTC);
		Long appointmentId = jdbcTemplate.queryForObject("""
			insert into appointments (patient_id, doctor_id, start_at, end_at, status, reason)
			values (?, ?, ?, ?, 'COMPLETED', 'Encounter integration fixture') returning id
			""", Long.class, fixture.patientId(), fixture.doctorId(), appointmentStart,
			appointmentStart.plusMinutes(30));
		var encounter = encounterService.create(new EncounterCreateRequest(fixture.patientId(), appointmentId,
			LocalDate.now(), "Visit notes", "Visit diagnosis"), fixture.doctorPrincipal());

		assertEquals(appointmentId, encounter.appointmentId());
		Fixture otherDoctor = createFixture();
		assertThrows(AccessDeniedException.class,
			() -> encounterService.getById(encounter.id(), otherDoctor.doctorPrincipal()));
	}

	@AfterEach
	void removeOnlyRowsCreatedByThisTest() {
		for (String email : patientEmails) {
			jdbcTemplate.update("delete from clinical_encounters where patient_id in "
				+ "(select p.id from patients p join users u on u.id = p.user_id where u.email = ?)", email);
			jdbcTemplate.update("delete from appointments where patient_id in "
				+ "(select p.id from patients p join users u on u.id = p.user_id where u.email = ?)", email);
			jdbcTemplate.update("delete from patients where user_id in (select id from users where email = ?)", email);
			jdbcTemplate.update("delete from users where email = ?", email);
		}
		for (String email : doctorEmails) {
			jdbcTemplate.update("delete from clinical_encounters where doctor_id in "
				+ "(select d.id from doctors d join users u on u.id = d.user_id where u.email = ?)", email);
			jdbcTemplate.update("delete from doctors where user_id in (select id from users where email = ?)", email);
			jdbcTemplate.update("delete from users where email = ?", email);
		}
	}

	private Fixture createFixture() {
		String doctorEmail = uniqueEmail("doctor");
		doctorEmails.add(doctorEmail);
		var doctor = doctorService.create(new DoctorCreateRequest(doctorEmail, "Encounter-test-password-42",
			"LIC-" + UUID.randomUUID(), "Encounter", "Doctor", "General Practice", null));
		String patientEmail = uniqueEmail("patient");
		patientEmails.add(patientEmail);
		var patient = patientService.register(new PatientRegistrationRequest(patientEmail,
			"Encounter-test-password-42", "Encounter", "Patient", null, null));
		patientService.assignDoctor(patient.id(), doctor.id());
		UserAccount doctorAccount = userRepository.findByEmailIgnoreCase(doctorEmail).orElseThrow();
		UserAccount patientAccount = userRepository.findByEmailIgnoreCase(patientEmail).orElseThrow();
		return new Fixture(patient.id(), doctor.id(), new HospitalUserPrincipal(doctorAccount),
			new HospitalUserPrincipal(patientAccount));
	}

	private static String uniqueEmail(String prefix) {
		return prefix + "+encounter-" + UUID.randomUUID() + "@example.test";
	}

	private record Fixture(Long patientId, Long doctorId, HospitalUserPrincipal doctorPrincipal,
			HospitalUserPrincipal patientPrincipal) {
	}
}