package com.smart_hospital_ai.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import com.smart_hospital_ai.demo.dto.AppointmentCreateRequest;
import com.smart_hospital_ai.demo.dto.AppointmentResponse;
import com.smart_hospital_ai.demo.dto.AppointmentRescheduleRequest;
import com.smart_hospital_ai.demo.dto.DoctorAvailabilityRequest;
import com.smart_hospital_ai.demo.dto.DoctorCreateRequest;
import com.smart_hospital_ai.demo.dto.PatientRegistrationRequest;
import com.smart_hospital_ai.demo.dto.PatientResponse;
import com.smart_hospital_ai.demo.entity.AppointmentStatus;
import com.smart_hospital_ai.demo.entity.UserAccount;
import com.smart_hospital_ai.demo.entity.UserRole;
import com.smart_hospital_ai.demo.exception.ApiException;
import com.smart_hospital_ai.demo.repository.AppointmentRepository;
import com.smart_hospital_ai.demo.repository.UserRepository;
import com.smart_hospital_ai.demo.security.HospitalUserPrincipal;
import com.smart_hospital_ai.demo.service.AppointmentService;
import com.smart_hospital_ai.demo.service.DoctorAvailabilityService;
import com.smart_hospital_ai.demo.service.DoctorService;
import com.smart_hospital_ai.demo.service.PatientService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(PostgresEnvironmentCondition.class)
class AppointmentPostgresIntegrationTest {

	@Autowired
	private AppointmentService appointmentService;

	@Autowired
	private PatientService patientService;

	@Autowired
	private DoctorService doctorService;

	@Autowired
	private DoctorAvailabilityService availabilityService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private AppointmentRepository appointmentRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	private final List<String> patientEmails = new ArrayList<>();
	private final List<String> doctorEmails = new ArrayList<>();

	@Test
	void persistsAppointmentRescheduleCancelAndPatientOwnership() {
		Fixture fixture = createFixture(2);
		HospitalUserPrincipal owner = fixture.patientPrincipals().get(0);
		HospitalUserPrincipal anotherPatient = fixture.patientPrincipals().get(1);

		var created = appointmentService.create(request(null, fixture.doctorId(), fixture.startAt()), owner);
		assertEquals(AppointmentStatus.SCHEDULED, created.status());
		assertThrows(AccessDeniedException.class, () -> appointmentService.getById(created.id(), anotherPatient));
		assertEquals(created.id(), appointmentService.getById(created.id(), owner).id());

		var moved = appointmentService.reschedule(created.id(),
			new AppointmentRescheduleRequest(fixture.startAt().plusMinutes(30), 30), owner);
		assertEquals(fixture.startAt().plusMinutes(30), moved.startAt());

		var cancelled = appointmentService.cancel(created.id(), owner);
		assertEquals(AppointmentStatus.CANCELLED, cancelled.status());
		assertTrue(cancelled.cancelledAt() != null);
	}

	@Test
	void patientCanBookWithoutAssignmentAndOnlyListsOwnAppointments() {
		Fixture fixture = createFixture(2);
		HospitalUserPrincipal owner = fixture.patientPrincipals().get(0);
		HospitalUserPrincipal anotherPatient = fixture.patientPrincipals().get(1);

		var created = appointmentService.create(request(null, fixture.doctorId(), fixture.startAt()), owner);
		var ownAppointments = appointmentService.search(null, null, null, null, null,
			0, 20, owner);
		var otherAppointments = appointmentService.search(null, null, null, null, null,
			0, 20, anotherPatient);

		assertEquals(fixture.patientIds().get(0), created.patientId());
		assertEquals(List.of(created.id()), ownAppointments.getContent().stream()
			.map(AppointmentResponse::id).toList());
		assertTrue(otherAppointments.isEmpty());
	}

	@Test
	void postBookingPersistsAndAppearsOnlyInAuthorizedRoleLists() throws Exception {
		Fixture fixture = createFixture(2);
		Fixture unrelatedDoctorFixture = createFixture(1);
		HospitalUserPrincipal patient = fixture.patientPrincipals().get(0);
		String body = String.format(Locale.ROOT,
			"{\"doctorId\":%d,\"startAt\":\"%s\",\"durationMinutes\":30,\"reason\":\"Integration test\"}",
			fixture.doctorId(), fixture.startAt());

		MvcResult result = mockMvc.perform(post("/api/v1/appointments")
				.with(user(patient))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isCreated())
			.andReturn();
		JsonNode payload = objectMapper.readTree(result.getResponse().getContentAsString());
		long appointmentId = payload.get("id").asLong();

		assertTrue(appointmentRepository.existsById(appointmentId));
		assertEquals(List.of(appointmentId), appointmentService.search(null, null, null, null, null,
			0, 20, patient).getContent().stream().map(AppointmentResponse::id).toList());
		assertEquals(List.of(appointmentId), appointmentService.search(null, null, null, null, null,
			0, 20, fixture.doctorPrincipal()).getContent().stream()
				.map(AppointmentResponse::id).toList());
		assertTrue(appointmentService.search(null, null, null, null, null, 0, 20,
			fixture.patientPrincipals().get(1)).isEmpty());
		assertTrue(appointmentService.search(null, null, null, null, null, 0, 20,
			unrelatedDoctorFixture.doctorPrincipal()).isEmpty());

		HospitalUserPrincipal admin = new HospitalUserPrincipal(new UserAccount(
			"appointment-admin-" + UUID.randomUUID() + "@example.test", "not-used", UserRole.ADMIN));
		assertEquals(List.of(appointmentId), appointmentService.search(null, null, null,
			fixture.patientIds().get(0), fixture.doctorId(), 0, 20, admin).getContent().stream()
				.map(AppointmentResponse::id).toList());
	}

	@Test
	void appointmentSearchReturnsMappedAppointmentsForAdminAndOnlyOwnAppointmentsForPatient() {
		Fixture fixture = createFixture(2);
		HospitalUserPrincipal owner = fixture.patientPrincipals().get(0);
		HospitalUserPrincipal anotherPatient = fixture.patientPrincipals().get(1);
		AppointmentResponse created = appointmentService.create(
			request(null, fixture.doctorId(), fixture.startAt()), owner);
		HospitalUserPrincipal admin = new HospitalUserPrincipal(new UserAccount(
			"appointment-admin-" + UUID.randomUUID() + "@example.test", "not-used", UserRole.ADMIN));

		var adminAppointments = appointmentService.search(null, null, null, null, fixture.doctorId(),
			0, 100, admin);
		var ownAppointments = appointmentService.search(null, null, null, null, fixture.doctorId(),
			0, 100, owner);
		var otherAppointments = appointmentService.search(null, null, null, null, fixture.doctorId(),
			0, 100, anotherPatient);

		assertEquals(1, adminAppointments.getTotalElements());
		assertEquals(created.id(), adminAppointments.getContent().get(0).id());
		assertEquals(created.id(), ownAppointments.getContent().get(0).id());
		assertEquals(created.medicalRecordNumber(), ownAppointments.getContent().get(0).medicalRecordNumber());
		assertEquals(created.doctorName(), ownAppointments.getContent().get(0).doctorName());
		assertTrue(otherAppointments.isEmpty());
	}

	@Test
	void appointmentSearchSupportsUnfilteredAndSingleDateBoundQueries() {
		Fixture fixture = createFixture(1);
		HospitalUserPrincipal owner = fixture.patientPrincipals().get(0);
		HospitalUserPrincipal admin = new HospitalUserPrincipal(new UserAccount(
			"appointment-admin-" + UUID.randomUUID() + "@example.test", "not-used", UserRole.ADMIN));
		AppointmentResponse first = appointmentService.create(
			request(null, fixture.doctorId(), fixture.startAt()), owner);
		AppointmentResponse second = appointmentService.create(
			request(null, fixture.doctorId(), fixture.startAt().plusMinutes(30)), owner);
		AppointmentResponse third = appointmentService.create(
			request(null, fixture.doctorId(), fixture.startAt().plusMinutes(60)), owner);

		assertEquals(List.of(first.id(), second.id(), third.id()),
			appointmentService.search(null, null, null, null, null, 0, 20, admin)
				.getContent().stream().map(AppointmentResponse::id).toList());
		assertEquals(List.of(second.id(), third.id()),
			appointmentService.search(null, second.startAt(), null, null, null, 0, 20, admin)
				.getContent().stream().map(AppointmentResponse::id).toList());
		assertEquals(List.of(first.id(), second.id()),
			appointmentService.search(null, null, third.startAt(), null, null, 0, 20, admin)
				.getContent().stream().map(AppointmentResponse::id).toList());
	}

	@Test
	void appointmentSearchSupportsOptionalFiltersBoundariesOrderingAndPagination() {
		Fixture fixture = createFixture(3);
		HospitalUserPrincipal owner = fixture.patientPrincipals().get(0);
		HospitalUserPrincipal otherPatient = fixture.patientPrincipals().get(1);

		AppointmentResponse first = appointmentService.create(
			request(null, fixture.doctorId(), fixture.startAt()), owner);
		AppointmentResponse second = appointmentService.create(
			request(null, fixture.doctorId(), fixture.startAt().plusMinutes(30)), owner);
		AppointmentResponse third = appointmentService.create(
			request(null, fixture.doctorId(), fixture.startAt().plusMinutes(60)), owner);
		AppointmentResponse fourth = appointmentService.create(
			request(null, fixture.doctorId(), fixture.startAt().plusMinutes(90)), owner);

		String secondDoctorEmail = uniqueEmail("doctor");
		doctorEmails.add(secondDoctorEmail);
		var secondDoctor = doctorService.create(new DoctorCreateRequest(secondDoctorEmail,
			"Appointment-test-password-42", "LIC-" + UUID.randomUUID(), "Second", "Doctor",
			"General Practice", null));
		HospitalUserPrincipal secondDoctorPrincipal = new HospitalUserPrincipal(
			userRepository.findByEmailIgnoreCase(secondDoctorEmail).orElseThrow());
		availabilityService.add(secondDoctor.id(), new DoctorAvailabilityRequest(
			(short) fixture.startAt().getDayOfWeek().getValue(), LocalTime.of(9, 0),
			LocalTime.of(13, 0)), secondDoctorPrincipal);
		AppointmentResponse fifth = appointmentService.create(
			request(null, secondDoctor.id(), fixture.startAt().plusMinutes(120)), owner);
		AppointmentResponse sixth = appointmentService.create(
			request(null, secondDoctor.id(), fixture.startAt().plusMinutes(150)), otherPatient);
		appointmentService.cancel(second.id(), owner);

		HospitalUserPrincipal admin = new HospitalUserPrincipal(new UserAccount(
			"appointment-admin-" + UUID.randomUUID() + "@example.test", "not-used", UserRole.ADMIN));
		var all = appointmentService.search(null, null, null, null, null, 0, 100, admin);
		List<Long> testAppointmentIds = List.of(first.id(), second.id(), third.id(), fourth.id(),
			fifth.id(), sixth.id());
		assertEquals(testAppointmentIds, all.getContent().stream()
			.filter(appointment -> testAppointmentIds.contains(appointment.id()))
			.map(AppointmentResponse::id).toList());
		assertEquals(List.of(first.id(), second.id(), third.id(), fourth.id(), fifth.id()),
			appointmentService.search(null, null, null, null, null, 0, 100, owner)
				.getContent().stream().map(AppointmentResponse::id).toList());
		assertEquals(List.of(sixth.id()),
			appointmentService.search(null, null, null, null, null, 0, 100, otherPatient)
				.getContent().stream().map(AppointmentResponse::id).toList());
		assertThrows(AccessDeniedException.class, () -> appointmentService.search(null, null, null,
			fixture.patientIds().get(1), null, 0, 100, owner));

		assertEquals(List.of(third.id(), fourth.id(), fifth.id()),
			appointmentService.search(null, fixture.startAt().plusMinutes(60), null,
				fixture.patientIds().get(0), null, 0, 100, admin)
				.getContent().stream().map(AppointmentResponse::id).toList());
		assertEquals(List.of(first.id(), second.id()),
			appointmentService.search(null, null, fixture.startAt().plusMinutes(60),
				fixture.patientIds().get(0), null, 0, 100, admin)
				.getContent().stream().map(AppointmentResponse::id).toList());
		assertEquals(List.of(second.id(), third.id()),
			appointmentService.search(null, fixture.startAt().plusMinutes(30),
				fixture.startAt().plusMinutes(90), fixture.patientIds().get(0), null, 0, 100, admin)
				.getContent().stream().map(AppointmentResponse::id).toList());
		assertEquals(List.of(fifth.id()),
			appointmentService.search(null, null, null, fixture.patientIds().get(0), secondDoctor.id(),
				0, 100, admin).getContent().stream().map(AppointmentResponse::id).toList());
		assertEquals(List.of(first.id(), second.id(), third.id(), fourth.id()),
			appointmentService.search(null, null, null, null, null, 0, 100,
				fixture.doctorPrincipal()).getContent().stream()
				.map(AppointmentResponse::id).toList());
		assertEquals(List.of(fifth.id(), sixth.id()),
			appointmentService.search(null, null, null, null, null, 0, 100,
				secondDoctorPrincipal).getContent().stream()
				.map(AppointmentResponse::id).toList());
		assertEquals(List.of(second.id()),
			appointmentService.search(AppointmentStatus.CANCELLED, null, null,
				fixture.patientIds().get(0), null,
				0, 100, admin).getContent().stream().map(AppointmentResponse::id).toList());

		assertEquals(List.of(first.id(), second.id()),
			appointmentService.search(null, null, null, fixture.patientIds().get(0), null, 0, 2, admin)
				.getContent().stream().map(AppointmentResponse::id).toList());
		assertEquals(List.of(third.id(), fourth.id()),
			appointmentService.search(null, null, null, fixture.patientIds().get(0), null, 1, 2, admin)
				.getContent().stream().map(AppointmentResponse::id).toList());
		assertEquals(List.of(fifth.id()),
			appointmentService.search(null, null, null, fixture.patientIds().get(0), null, 2, 2, admin)
				.getContent().stream().map(AppointmentResponse::id).toList());
	}

	@Test
	void allowsAdjacentDoctorBookingsAndRejectsOverlappingBookingsAcrossOffsets() {
		Fixture fixture = createFixture(3);
		appointmentService.create(request(fixture.patientIds().get(0), fixture.doctorId(),
			fixture.startAt()), fixture.patientPrincipals().get(0));

		AppointmentResponse adjacent = appointmentService.create(request(fixture.patientIds().get(1),
			fixture.doctorId(), fixture.startAt().plusMinutes(30)), fixture.patientPrincipals().get(1));
		assertEquals(fixture.startAt().plusMinutes(30), adjacent.startAt());

		ApiException conflict = assertThrows(ApiException.class, () -> appointmentService.create(
			request(fixture.patientIds().get(2), fixture.doctorId(), fixture.startAt().plusMinutes(15)),
			fixture.patientPrincipals().get(2)));
		assertEquals(409, conflict.getStatus().value());
		assertEquals("Doctor already has an overlapping appointment", conflict.getMessage());

		OffsetDateTime sameInstantWithDifferentOffset = fixture.startAt()
			.withOffsetSameInstant(ZoneOffset.UTC);
		assertTrue(appointmentRepository.existsDoctorOverlap(fixture.doctorId(),
			sameInstantWithDifferentOffset, sameInstantWithDifferentOffset.plusMinutes(15),
			AppointmentStatus.SCHEDULED, null));
		assertFalse(appointmentRepository.existsDoctorOverlap(fixture.doctorId(),
			sameInstantWithDifferentOffset.plusMinutes(60),
			sameInstantWithDifferentOffset.plusMinutes(75), AppointmentStatus.SCHEDULED, null));
	}

	@Test
	void concurrentBookingsForSameDoctorCannotOverlap() throws Exception {
		Fixture fixture = createFixture(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);

		try {
			List<Future<String>> results = new ArrayList<>();
			for (int index = 0; index < 2; index++) {
				int patientIndex = index;
				results.add(executor.submit(() -> {
					ready.countDown();
					start.await();
					try {
						appointmentService.create(request(fixture.patientIds().get(patientIndex),
							fixture.doctorId(), fixture.startAt()), fixture.patientPrincipals().get(patientIndex));
						return "BOOKED";
					} catch (ApiException exception) {
						if (exception.getStatus().value() == 409) {
							return "CONFLICT";
						}
						throw exception;
					}
				}));
			}
			ready.await();
			start.countDown();
			List<String> outcomes = List.of(results.get(0).get(), results.get(1).get());
			assertEquals(1, outcomes.stream().filter("BOOKED"::equals).count());
			assertEquals(1, outcomes.stream().filter("CONFLICT"::equals).count());
		} finally {
			start.countDown();
			executor.shutdownNow();
		}
	}

	@AfterEach
	void removeOnlyRowsCreatedByThisTest() {
		for (String email : patientEmails) {
			jdbcTemplate.update("delete from appointments where patient_id in "
				+ "(select p.id from patients p join users u on u.id = p.user_id where u.email = ?)", email);
			jdbcTemplate.update("delete from patients where user_id in (select id from users where email = ?)", email);
			jdbcTemplate.update("delete from users where email = ?", email);
		}
		for (String email : doctorEmails) {
			jdbcTemplate.update("delete from appointments where doctor_id in "
				+ "(select d.id from doctors d join users u on u.id = d.user_id where u.email = ?)", email);
			jdbcTemplate.update("delete from doctors where user_id in (select id from users where email = ?)", email);
			jdbcTemplate.update("delete from users where email = ?", email);
		}
	}

	private Fixture createFixture(int patientCount) {
		String doctorEmail = uniqueEmail("doctor");
		doctorEmails.add(doctorEmail);
		var doctor = doctorService.create(new DoctorCreateRequest(doctorEmail, "Appointment-test-password-42",
			"LIC-" + UUID.randomUUID(), "Appointment", "Doctor", "General Practice", null));
		UserAccount doctorAccount = userRepository.findByEmailIgnoreCase(doctorEmail).orElseThrow();
		HospitalUserPrincipal doctorPrincipal = new HospitalUserPrincipal(doctorAccount);
		ZoneId hospitalTimeZone = ZoneId.of("Asia/Kolkata");
		OffsetDateTime startAt = LocalDate.now(hospitalTimeZone).plusDays(8)
			.atTime(10, 0).atZone(hospitalTimeZone).toOffsetDateTime();
		availabilityService.add(doctor.id(), new DoctorAvailabilityRequest(
			(short) startAt.getDayOfWeek().getValue(), LocalTime.of(9, 0), LocalTime.of(12, 0)), doctorPrincipal);

		List<Long> patientIds = new ArrayList<>();
		List<HospitalUserPrincipal> principals = new ArrayList<>();
		for (int index = 0; index < patientCount; index++) {
			String patientEmail = uniqueEmail("patient");
			patientEmails.add(patientEmail);
			PatientResponse patient = patientService.register(new PatientRegistrationRequest(patientEmail,
				"Appointment-test-password-42", "Appointment", "Patient", null, null));
			UserAccount patientAccount = userRepository.findByEmailIgnoreCase(patientEmail).orElseThrow();
			patientIds.add(patient.id());
			principals.add(new HospitalUserPrincipal(patientAccount));
		}
		return new Fixture(doctor.id(), doctorPrincipal, startAt, patientIds, principals);
	}

	private static AppointmentCreateRequest request(Long patientId, Long doctorId, OffsetDateTime startAt) {
		return new AppointmentCreateRequest(patientId, doctorId, startAt, 30, "Integration test");
	}

	private static String uniqueEmail(String prefix) {
		return prefix + "+appointment-" + UUID.randomUUID() + "@example.test";
	}

	private record Fixture(Long doctorId, HospitalUserPrincipal doctorPrincipal, OffsetDateTime startAt,
			List<Long> patientIds, List<HospitalUserPrincipal> patientPrincipals) {
	}
}