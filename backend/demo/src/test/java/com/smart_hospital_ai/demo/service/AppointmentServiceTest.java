package com.smart_hospital_ai.demo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import com.smart_hospital_ai.demo.dto.AppointmentCreateRequest;
import com.smart_hospital_ai.demo.dto.AppointmentResponse;
import com.smart_hospital_ai.demo.dto.AppointmentRescheduleRequest;
import com.smart_hospital_ai.demo.entity.Appointment;
import com.smart_hospital_ai.demo.entity.AppointmentStatus;
import com.smart_hospital_ai.demo.entity.Doctor;
import com.smart_hospital_ai.demo.entity.DoctorAvailability;
import com.smart_hospital_ai.demo.entity.Patient;
import com.smart_hospital_ai.demo.entity.UserAccount;
import com.smart_hospital_ai.demo.entity.UserRole;
import com.smart_hospital_ai.demo.exception.ApiException;
import com.smart_hospital_ai.demo.repository.AppointmentRepository;
import com.smart_hospital_ai.demo.repository.DoctorAvailabilityRepository;
import com.smart_hospital_ai.demo.repository.DoctorRepository;
import com.smart_hospital_ai.demo.repository.PatientRepository;
import com.smart_hospital_ai.demo.security.HospitalUserPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.security.access.AccessDeniedException;

class AppointmentServiceTest {

	private final AppointmentRepository appointmentRepository = mock(AppointmentRepository.class);
	private final PatientRepository patientRepository = mock(PatientRepository.class);
	private final DoctorRepository doctorRepository = mock(DoctorRepository.class);
	private final DoctorAvailabilityRepository availabilityRepository = mock(DoctorAvailabilityRepository.class);
	private final AppointmentService service = new AppointmentService(appointmentRepository,
		patientRepository, doctorRepository, availabilityRepository, "Asia/Kolkata");
	private final Patient patient = mock(Patient.class);
	private final Doctor doctor = mock(Doctor.class);
	private final DoctorAvailability availability = mock(DoctorAvailability.class);
	private final UserAccount patientAccount = mock(UserAccount.class);
	private final UserAccount doctorAccount = mock(UserAccount.class);
	private HospitalUserPrincipal patientPrincipal;
	private final OffsetDateTime startAt = LocalDate.now(ZoneOffset.UTC).plusDays(2)
		.atTime(9, 0).atOffset(ZoneOffset.UTC);

	@BeforeEach
	void setUp() {
		when(patientAccount.getId()).thenReturn(10L);
		when(patientAccount.getEmail()).thenReturn("patient@example.test");
		when(patientAccount.getPasswordHash()).thenReturn("$2a$encoded");
		when(patientAccount.getRole()).thenReturn(UserRole.PATIENT);
		when(patientAccount.isActive()).thenReturn(true);
		patientPrincipal = new HospitalUserPrincipal(patientAccount);
		when(patientRepository.findByUserId(10L)).thenReturn(Optional.of(patient));
		when(patient.getId()).thenReturn(20L);
		when(patient.getUser()).thenReturn(patientAccount);
		when(patient.getAssignedDoctor()).thenReturn(doctor);
		when(patientRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(patient));
		when(doctor.getId()).thenReturn(30L);
		when(doctor.getUser()).thenReturn(doctorAccount);
		when(doctorAccount.isActive()).thenReturn(true);
		when(doctorRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(doctor));
		when(availabilityRepository.findByDoctorIdAndDayOfWeek(30L,
			(short) startAt.getDayOfWeek().getValue())).thenReturn(List.of(availability));
		when(availability.getStartTime()).thenReturn(LocalTime.of(8, 0));
		when(availability.getEndTime()).thenReturn(LocalTime.of(17, 0));
		when(appointmentRepository.existsDoctorOverlap(30L, startAt, startAt.plusMinutes(30),
			AppointmentStatus.SCHEDULED, null)).thenReturn(false);
		when(appointmentRepository.existsPatientOverlap(20L, startAt, startAt.plusMinutes(30),
			AppointmentStatus.SCHEDULED, null)).thenReturn(false);
	}

	@Test
	void locksDoctorThenPatientBeforeCheckingConflictsAndSaving() {
		when(appointmentRepository.save(org.mockito.ArgumentMatchers.any(Appointment.class)))
			.thenAnswer(invocation -> invocation.getArgument(0));

		service.create(new AppointmentCreateRequest(null, 30L, startAt, 30, "Consultation"), patientPrincipal);

		InOrder lockOrder = inOrder(doctorRepository, patientRepository);
		lockOrder.verify(doctorRepository).findByIdForUpdate(30L);
		lockOrder.verify(patientRepository).findByIdForUpdate(20L);
		verify(appointmentRepository).save(org.mockito.ArgumentMatchers.any(Appointment.class));
	}

	@Test
	void rejectsOverlappingDoctorBooking() {
		when(appointmentRepository.existsDoctorOverlap(30L, startAt, startAt.plusMinutes(30),
			AppointmentStatus.SCHEDULED, null)).thenReturn(true);

		ApiException exception = assertThrows(ApiException.class, () -> service.create(
			new AppointmentCreateRequest(null, 30L, startAt, 30, null), patientPrincipal));

		assertEquals(409, exception.getStatus().value());
		verify(appointmentRepository, never()).save(org.mockito.ArgumentMatchers.any(Appointment.class));
	}

	@Test
	void allowsPatientToBookDoctorWithoutPriorAssignment() {
		when(patient.getAssignedDoctor()).thenReturn(null);
		when(appointmentRepository.save(any(Appointment.class)))
			.thenAnswer(invocation -> invocation.getArgument(0));

		AppointmentResponse response = service.create(
			new AppointmentCreateRequest(null, 30L, startAt, 30, null), patientPrincipal);

		assertEquals(20L, response.patientId());
		assertEquals(30L, response.doctorId());
		verify(appointmentRepository).save(any(Appointment.class));
	}

	@Test
	void rejectsAppointmentsOutsideDoctorAvailabilityAsInvalidRequests() {
		OffsetDateTime outsideAvailability = startAt.withHour(18);
		ApiException exception = assertThrows(ApiException.class, () -> service.create(
			new AppointmentCreateRequest(null, 30L, outsideAvailability, 30, null), patientPrincipal));

		assertEquals(400, exception.getStatus().value());
	}

	@Test
	void rejectsInactiveDoctors() {
		when(doctorAccount.isActive()).thenReturn(false);

		ApiException exception = assertThrows(ApiException.class, () -> service.create(
			new AppointmentCreateRequest(null, 30L, startAt, 30, null), patientPrincipal));

		assertEquals(409, exception.getStatus().value());
	}

	@Test
	void rejectsOverlappingPatientBooking() {
		when(appointmentRepository.existsPatientOverlap(20L, startAt, startAt.plusMinutes(30),
			AppointmentStatus.SCHEDULED, null)).thenReturn(true);

		ApiException exception = assertThrows(ApiException.class, () -> service.create(
			new AppointmentCreateRequest(null, 30L, startAt, 30, null), patientPrincipal));

		assertEquals(409, exception.getStatus().value());
	}

	@Test
	void rejectsCancellationOfAppointmentStartingInThePast() {
		OffsetDateTime now = OffsetDateTime.parse("2030-01-01T10:30:00+05:30");
		Appointment appointment = mockManageableScheduledAppointment(
			now.minusSeconds(1).withOffsetSameInstant(ZoneOffset.UTC));
		AppointmentService fixedTimeService = serviceAt(now);

		ApiException exception = assertThrows(ApiException.class,
			() -> fixedTimeService.cancel(70L, adminPrincipal()));

		assertEquals(409, exception.getStatus().value());
		assertEquals("Appointments can only be cancelled more than 24 hours before their start time",
			exception.getMessage());
		verify(appointment, never()).cancel();
		verify(doctorRepository, never()).findByIdForUpdate(30L);
	}

	@Test
	void rejectsCancellationExactly24HoursBeforeAppointmentAcrossOffsets() {
		OffsetDateTime now = OffsetDateTime.parse("2030-01-01T10:30:00+05:30");
		Appointment appointment = mockManageableScheduledAppointment(
			now.plusHours(24).withOffsetSameInstant(ZoneOffset.UTC));
		AppointmentService fixedTimeService = serviceAt(now);

		ApiException exception = assertThrows(ApiException.class,
			() -> fixedTimeService.cancel(70L, adminPrincipal()));

		assertEquals(409, exception.getStatus().value());
		assertEquals("Appointments can only be cancelled more than 24 hours before their start time",
			exception.getMessage());
		verify(appointment, never()).cancel();
		verify(doctorRepository, never()).findByIdForUpdate(30L);
	}

	@Test
	void rejectsCancellationLessThan24HoursBeforeAppointment() {
		OffsetDateTime now = OffsetDateTime.parse("2030-01-01T10:30:00+05:30");
		Appointment appointment = mockManageableScheduledAppointment(
			now.plusHours(23).withOffsetSameInstant(ZoneOffset.UTC));
		AppointmentService fixedTimeService = serviceAt(now);

		ApiException exception = assertThrows(ApiException.class,
			() -> fixedTimeService.cancel(70L, adminPrincipal()));

		assertEquals(409, exception.getStatus().value());
		assertEquals("Appointments can only be cancelled more than 24 hours before their start time",
			exception.getMessage());
		verify(appointment, never()).cancel();
		verify(doctorRepository, never()).findByIdForUpdate(30L);
	}

	@Test
	void rejectsReschedulingAppointmentAtTheCurrentInstantAcrossOffsets() {
		OffsetDateTime now = OffsetDateTime.parse("2030-01-01T10:30:00+05:30");
		Appointment appointment = mockManageableScheduledAppointment(
			now.withOffsetSameInstant(ZoneOffset.UTC));
		AppointmentService fixedTimeService = serviceAt(now);

		ApiException exception = assertThrows(ApiException.class, () -> fixedTimeService.reschedule(
			70L, new AppointmentRescheduleRequest(now.plusDays(1), 30), adminPrincipal()));

		assertEquals(409, exception.getStatus().value());
		verify(doctorRepository, never()).findByIdForUpdate(30L);
	}

	@Test
	void rejectsReschedulingAppointmentStartingInThePast() {
		OffsetDateTime now = OffsetDateTime.parse("2030-01-01T10:30:00+05:30");
		Appointment appointment = mockManageableScheduledAppointment(
			now.minusSeconds(1).withOffsetSameInstant(ZoneOffset.UTC));
		AppointmentService fixedTimeService = serviceAt(now);

		ApiException exception = assertThrows(ApiException.class, () -> fixedTimeService.reschedule(
			70L, new AppointmentRescheduleRequest(now.plusDays(1), 30), adminPrincipal()));

		assertEquals(409, exception.getStatus().value());
		verify(appointment, never()).reschedule(any(), any());
		verify(doctorRepository, never()).findByIdForUpdate(30L);
	}

	@Test
	void permitsCancellationOfAppointmentStartingInTheFuture() {
		OffsetDateTime now = OffsetDateTime.parse("2030-01-01T10:30:00+05:30");
		Appointment appointment = mockManageableScheduledAppointment(
			now.plusHours(24).plusSeconds(1).withOffsetSameInstant(ZoneOffset.UTC));
		AppointmentService fixedTimeService = serviceAt(now);

		fixedTimeService.cancel(70L, adminPrincipal());

		verify(appointment).cancel();
		verify(doctorRepository).findByIdForUpdate(30L);
		verify(patientRepository).findByIdForUpdate(20L);
	}

	@Test
	void permitsReschedulingAppointmentStartingInTheFuture() {
		OffsetDateTime now = OffsetDateTime.parse("2030-01-01T10:30:00+05:30");
		OffsetDateTime originalStartAt = now.plusHours(1);
		OffsetDateTime rescheduledStartAt = now.plusDays(1).withHour(10).withMinute(45);
		Appointment appointment = mockManageableScheduledAppointment(originalStartAt);
		AppointmentService fixedTimeService = serviceAt(now);
		when(availabilityRepository.findByDoctorIdAndDayOfWeek(30L,
			(short) rescheduledStartAt.getDayOfWeek().getValue())).thenReturn(List.of(availability));
		when(appointmentRepository.existsDoctorOverlap(30L, rescheduledStartAt,
			rescheduledStartAt.plusMinutes(30), AppointmentStatus.SCHEDULED, 70L)).thenReturn(false);
		when(appointmentRepository.existsPatientOverlap(20L, rescheduledStartAt,
			rescheduledStartAt.plusMinutes(30), AppointmentStatus.SCHEDULED, 70L)).thenReturn(false);

		fixedTimeService.reschedule(70L,
			new AppointmentRescheduleRequest(rescheduledStartAt, 30), adminPrincipal());

		verify(appointment).reschedule(rescheduledStartAt, rescheduledStartAt.plusMinutes(30));
	}

	@Test
	void patientAppointmentSearchAlwaysUsesAuthenticatedPatientProfile() {
		when(appointmentRepository.findAll(any(Specification.class), any(Pageable.class)))
			.thenReturn(Page.empty());

		service.search(null, null, null, null, null, 0, 20, patientPrincipal);

		verify(appointmentRepository).findAll(any(Specification.class), any(Pageable.class));
	}

	@Test
	void doctorAppointmentSearchAlwaysUsesAuthenticatedDoctorProfile() {
		when(doctorAccount.getId()).thenReturn(30L);
		when(doctorAccount.getEmail()).thenReturn("doctor@example.test");
		when(doctorAccount.getPasswordHash()).thenReturn("$2a$encoded");
		when(doctorAccount.getRole()).thenReturn(UserRole.DOCTOR);
		when(doctorAccount.isActive()).thenReturn(true);
		when(doctorRepository.findByUserId(30L)).thenReturn(Optional.of(doctor));
		when(appointmentRepository.findAll(any(Specification.class), any(Pageable.class)))
			.thenReturn(Page.empty());
		HospitalUserPrincipal doctorPrincipal = new HospitalUserPrincipal(doctorAccount);
		ArgumentCaptor<Specification<Appointment>> specification =
			ArgumentCaptor.forClass(Specification.class);

		service.search(null, null, null, null, null, 0, 20, doctorPrincipal);

		verify(appointmentRepository).findAll(specification.capture(), any(Pageable.class));
		Root<Appointment> root = mock(Root.class);
		Path<Doctor> doctorPath = mock(Path.class);
		Path<Long> doctorIdPath = mock(Path.class);
		when(root.<Doctor>get("doctor")).thenReturn(doctorPath);
		when(doctorPath.<Long>get("id")).thenReturn(doctorIdPath);
		CriteriaBuilder criteriaBuilder = mock(CriteriaBuilder.class);
		specification.getValue().toPredicate(root, null, criteriaBuilder);

		verify(criteriaBuilder).equal(doctorIdPath, 30L);
		verify(doctorRepository).findByUserId(30L);
		verify(patientRepository, never()).findByUserId(30L);
	}

	@Test
	void unfilteredAppointmentSearchDoesNotCreateOptionalPredicates() {
		HospitalUserPrincipal admin = new HospitalUserPrincipal(
			new UserAccount("admin@example.test", "not-used", UserRole.ADMIN));
		ArgumentCaptor<Specification<Appointment>> specification =
			ArgumentCaptor.forClass(Specification.class);
		when(appointmentRepository.findAll(any(Specification.class), any(Pageable.class)))
			.thenReturn(Page.empty());

		service.search(null, null, null, null, null, 0, 20, admin);
		verify(appointmentRepository).findAll(specification.capture(), any(Pageable.class));

		Root<Appointment> root = mock(Root.class);
		CriteriaBuilder criteriaBuilder = mock(CriteriaBuilder.class);
		specification.getValue().toPredicate(root, null, criteriaBuilder);

		verify(root, never()).get(any(String.class));
		verify(criteriaBuilder).conjunction();
	}

	@Test
	void appointmentSearchAddsOnlyTheProvidedDateBound() {
		HospitalUserPrincipal admin = new HospitalUserPrincipal(
			new UserAccount("admin@example.test", "not-used", UserRole.ADMIN));
		OffsetDateTime fromAt = startAt.minusHours(1);
		ArgumentCaptor<Specification<Appointment>> specification =
			ArgumentCaptor.forClass(Specification.class);
		when(appointmentRepository.findAll(any(Specification.class), any(Pageable.class)))
			.thenReturn(Page.empty());

		service.search(null, fromAt, null, null, null, 0, 20, admin);
		verify(appointmentRepository).findAll(specification.capture(), any(Pageable.class));

		Root<Appointment> root = mock(Root.class);
		Path<OffsetDateTime> startAtPath = mock(Path.class);
		when(root.<OffsetDateTime>get("startAt")).thenReturn(startAtPath);
		CriteriaBuilder criteriaBuilder = mock(CriteriaBuilder.class);
		specification.getValue().toPredicate(root, null, criteriaBuilder);

		verify(criteriaBuilder).greaterThanOrEqualTo(startAtPath, fromAt);
		verify(root, times(1)).get("startAt");
	}

	@Test
	void appointmentSearchAddsOnlyTheProvidedUpperDateBound() {
		HospitalUserPrincipal admin = new HospitalUserPrincipal(
			new UserAccount("admin@example.test", "not-used", UserRole.ADMIN));
		OffsetDateTime toAt = startAt.plusHours(1);
		ArgumentCaptor<Specification<Appointment>> specification =
			ArgumentCaptor.forClass(Specification.class);
		when(appointmentRepository.findAll(any(Specification.class), any(Pageable.class)))
			.thenReturn(Page.empty());

		service.search(null, null, toAt, null, null, 0, 20, admin);
		verify(appointmentRepository).findAll(specification.capture(), any(Pageable.class));

		Root<Appointment> root = mock(Root.class);
		Path<OffsetDateTime> startAtPath = mock(Path.class);
		when(root.<OffsetDateTime>get("startAt")).thenReturn(startAtPath);
		CriteriaBuilder criteriaBuilder = mock(CriteriaBuilder.class);
		specification.getValue().toPredicate(root, null, criteriaBuilder);

		verify(criteriaBuilder).lessThan(startAtPath, toAt);
		verify(root, times(1)).get("startAt");
	}

	@Test
	void appointmentSearchUsesTypedInclusiveStartAndExclusiveEndDateBounds() {
		HospitalUserPrincipal admin = new HospitalUserPrincipal(
			new UserAccount("admin@example.test", "not-used", UserRole.ADMIN));
		OffsetDateTime fromAt = startAt.minusHours(1);
		OffsetDateTime toAt = startAt.plusHours(1);
		ArgumentCaptor<Specification<Appointment>> specification =
			ArgumentCaptor.forClass(Specification.class);
		when(appointmentRepository.findAll(any(Specification.class), any(Pageable.class)))
			.thenReturn(Page.empty());

		service.search(null, fromAt, toAt, null, null, 0, 20, admin);
		verify(appointmentRepository).findAll(specification.capture(), any(Pageable.class));

		Root<Appointment> root = mock(Root.class);
		Path<OffsetDateTime> startAtPath = mock(Path.class);
		when(root.<OffsetDateTime>get("startAt")).thenReturn(startAtPath);
		CriteriaBuilder criteriaBuilder = mock(CriteriaBuilder.class);
		specification.getValue().toPredicate(root, null, criteriaBuilder);

		verify(criteriaBuilder).greaterThanOrEqualTo(startAtPath, fromAt);
		verify(criteriaBuilder).lessThan(startAtPath, toAt);
		verify(root, times(2)).get("startAt");
	}

	@Test
	void appointmentSearchMapsPageContentToResponseDtos() {
		Appointment appointment = mock(Appointment.class);
		when(appointment.getId()).thenReturn(40L);
		when(appointment.getPatient()).thenReturn(patient);
		when(appointment.getDoctor()).thenReturn(doctor);
		when(appointment.getStartAt()).thenReturn(startAt);
		when(appointment.getEndAt()).thenReturn(startAt.plusMinutes(30));
		when(appointment.getStatus()).thenReturn(AppointmentStatus.SCHEDULED);
		when(patient.getMedicalRecordNumber()).thenReturn("MRN-TEST");
		when(doctor.getFirstName()).thenReturn("Test");
		when(doctor.getLastName()).thenReturn("Doctor");
		when(appointmentRepository.findAll(any(Specification.class), any(Pageable.class)))
			.thenReturn(new PageImpl<>(List.of(appointment),
				PageRequest.of(0, 20), 1));

		Page<AppointmentResponse> results = service.search(null, null, null, null, null,
			0, 20, patientPrincipal);

		assertEquals(1, results.getTotalElements());
		assertEquals(40L, results.getContent().get(0).id());
		assertEquals("MRN-TEST", results.getContent().get(0).medicalRecordNumber());
		assertEquals("Test Doctor", results.getContent().get(0).doctorName());
		verify(appointmentRepository).findAll(any(Specification.class), any(Pageable.class));
	}

	@Test
	void patientCannotFilterAppointmentSearchToAnotherPatient() {
		assertThrows(AccessDeniedException.class, () -> service.search(null, null, null,
			999L, null, 0, 20, patientPrincipal));
		verify(appointmentRepository, never()).findAll(any(Specification.class), any(Pageable.class));
	}

	@Test
	void rejectsPatientBookingForAnotherAccount() {
		assertThrows(AccessDeniedException.class, () -> service.create(
			new AppointmentCreateRequest(999L, 30L, startAt, 30, null), patientPrincipal));
	}

	@Test
	void acceptsAppointmentWhenSlotMatchesLocalDoctorAvailability() {
		OffsetDateTime mondayAtLocal = OffsetDateTime.parse("2026-10-26T11:45:00+00:00");
		when(availabilityRepository.findByDoctorIdAndDayOfWeek(30L, (short) 1)).thenReturn(List.of(availability));
		when(availability.getStartTime()).thenReturn(LocalTime.of(9, 0));
		when(availability.getEndTime()).thenReturn(LocalTime.of(21, 0));
		when(appointmentRepository.save(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));

		AppointmentResponse response = service.create(
			new AppointmentCreateRequest(null, 30L, mondayAtLocal, 30, null), patientPrincipal);

		assertEquals(30L, response.doctorId());
		assertEquals(20L, response.patientId());
		assertEquals(11, response.startAt().toLocalTime().getHour());
		assertEquals(45, response.startAt().toLocalTime().getMinute());
	}

	@Test
	void validatesIsoInstantAgainstConfiguredHospitalLocalTime() {
		ZoneId hospitalTimeZone = ZoneId.of("Asia/Kolkata");
		OffsetDateTime hospitalStart = LocalDate.now(hospitalTimeZone).plusDays(2)
			.atTime(12, 45).atZone(hospitalTimeZone).toOffsetDateTime();
		OffsetDateTime requestStart = hospitalStart.withOffsetSameInstant(ZoneOffset.UTC);
		when(availabilityRepository.findByDoctorIdAndDayOfWeek(30L,
			(short) hospitalStart.getDayOfWeek().getValue())).thenReturn(List.of(availability));
		when(availability.getStartTime()).thenReturn(LocalTime.of(12, 0));
		when(availability.getEndTime()).thenReturn(LocalTime.of(13, 30));
		when(appointmentRepository.save(any(Appointment.class)))
			.thenAnswer(invocation -> invocation.getArgument(0));

		AppointmentResponse response = service.create(
			new AppointmentCreateRequest(null, 30L, requestStart, 30, null), patientPrincipal);

		assertEquals(requestStart, response.startAt());
		assertEquals(requestStart.plusMinutes(30), response.endAt());
		verify(availabilityRepository).findByDoctorIdAndDayOfWeek(30L,
			(short) hospitalStart.getDayOfWeek().getValue());
	}

	@Test
	void rejectsAppointmentOutsideDoctorAvailabilityAfterLocalTimeNormalization() {
		OffsetDateTime mondayAtLocal = OffsetDateTime.parse("2026-10-26T21:15:00+00:00");
		when(availabilityRepository.findByDoctorIdAndDayOfWeek(30L, (short) 1)).thenReturn(List.of(availability));
		when(availability.getStartTime()).thenReturn(LocalTime.of(9, 0));
		when(availability.getEndTime()).thenReturn(LocalTime.of(21, 0));

		ApiException exception = assertThrows(ApiException.class, () -> service.create(
			new AppointmentCreateRequest(null, 30L, mondayAtLocal, 30, null), patientPrincipal));

		assertEquals(400, exception.getStatus().value());
	}

	private AppointmentService serviceAt(OffsetDateTime now) {
		return new AppointmentService(appointmentRepository, patientRepository, doctorRepository,
			availabilityRepository, "Asia/Kolkata", Clock.fixed(now.toInstant(), ZoneOffset.UTC));
	}

	private Appointment mockManageableScheduledAppointment(OffsetDateTime startAt) {
		Appointment appointment = mock(Appointment.class);
		when(appointmentRepository.findByIdForUpdate(70L)).thenReturn(Optional.of(appointment));
		when(appointment.getStatus()).thenReturn(AppointmentStatus.SCHEDULED);
		when(appointment.getStartAt()).thenReturn(startAt);
		when(appointment.getDoctor()).thenReturn(doctor);
		when(appointment.getPatient()).thenReturn(patient);
		when(patient.getMedicalRecordNumber()).thenReturn("MRN-TEST");
		when(doctor.getFirstName()).thenReturn("Test");
		when(doctor.getLastName()).thenReturn("Doctor");
		return appointment;
	}

	private static HospitalUserPrincipal adminPrincipal() {
		return new HospitalUserPrincipal(
			new UserAccount("admin@example.test", "not-used", UserRole.ADMIN));
	}
}