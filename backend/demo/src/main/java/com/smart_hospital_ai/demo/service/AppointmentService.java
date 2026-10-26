package com.smart_hospital_ai.demo.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import com.smart_hospital_ai.demo.dto.AppointmentCreateRequest;
import com.smart_hospital_ai.demo.dto.AppointmentResponse;
import com.smart_hospital_ai.demo.dto.AppointmentRescheduleRequest;
import com.smart_hospital_ai.demo.entity.Appointment;
import com.smart_hospital_ai.demo.entity.AppointmentStatus;
import com.smart_hospital_ai.demo.entity.Doctor;
import com.smart_hospital_ai.demo.entity.DoctorAvailability;
import com.smart_hospital_ai.demo.entity.Patient;
import com.smart_hospital_ai.demo.entity.UserRole;
import com.smart_hospital_ai.demo.exception.ApiException;
import com.smart_hospital_ai.demo.repository.AppointmentRepository;
import com.smart_hospital_ai.demo.repository.DoctorAvailabilityRepository;
import com.smart_hospital_ai.demo.repository.DoctorRepository;
import com.smart_hospital_ai.demo.repository.PatientRepository;
import com.smart_hospital_ai.demo.security.HospitalUserPrincipal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppointmentService {

	private static final int MAX_PAGE_SIZE = 100;
	private static final List<UserRole> APPOINTMENT_ROLES = List.of(
		UserRole.ADMIN, UserRole.DOCTOR, UserRole.RECEPTIONIST, UserRole.PATIENT);

	private final AppointmentRepository appointmentRepository;
	private final PatientRepository patientRepository;
	private final DoctorRepository doctorRepository;
	private final DoctorAvailabilityRepository availabilityRepository;
	private final ZoneId hospitalTimeZone;
	private final Clock clock;

	@Autowired
	public AppointmentService(AppointmentRepository appointmentRepository,
			PatientRepository patientRepository,
			DoctorRepository doctorRepository,
			DoctorAvailabilityRepository availabilityRepository,
			@Value("${app.hospital.time-zone:Asia/Kolkata}") String hospitalTimeZone) {
		this(appointmentRepository, patientRepository, doctorRepository,
			availabilityRepository, hospitalTimeZone, Clock.systemUTC());
	}

	AppointmentService(AppointmentRepository appointmentRepository,
			PatientRepository patientRepository,
			DoctorRepository doctorRepository,
			DoctorAvailabilityRepository availabilityRepository,
			String hospitalTimeZone, Clock clock) {
		this.appointmentRepository = appointmentRepository;
		this.patientRepository = patientRepository;
		this.doctorRepository = doctorRepository;
		this.availabilityRepository = availabilityRepository;
		this.hospitalTimeZone = ZoneId.of(hospitalTimeZone);
		this.clock = clock;
	}

	@Transactional
	public AppointmentResponse create(AppointmentCreateRequest request, HospitalUserPrincipal principal) {
		assertAppointmentRole(principal);
		Long doctorId = resolveDoctorId(request.doctorId(), principal);
		Long patientId = resolvePatientId(request.patientId(), principal);

		Doctor doctor = doctorRepository.findByIdForUpdate(doctorId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Doctor not found"));
		Patient patient = patientRepository.findByIdForUpdate(patientId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Patient not found"));
		assertDoctorIsActive(doctor);
		assertBookingAccess(patient, doctor, principal);

		OffsetDateTime startAt = request.startAt();
		OffsetDateTime endAt = startAt.plusMinutes(request.durationMinutes());
		validateAppointmentTime(doctorId, startAt, endAt, request.durationMinutes());
		assertNoOverlap(patientId, doctorId, startAt, endAt, null);

		Appointment appointment = new Appointment(patient, doctor, startAt, endAt, blankToNull(request.reason()));
		return toResponse(appointmentRepository.save(appointment));
	}

	@Transactional(readOnly = true)
	public Page<AppointmentResponse> search(AppointmentStatus status, OffsetDateTime fromAt,
			OffsetDateTime toAt, Long requestedPatientId, Long requestedDoctorId,
			int page, int size, HospitalUserPrincipal principal) {
		assertAppointmentRole(principal);
		if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Page must be non-negative and size must be between 1 and 100");
		}
		if (fromAt != null && toAt != null && !fromAt.isBefore(toAt)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "fromAt must be earlier than toAt");
		}

		Long patientId = requestedPatientId;
		Long doctorId = requestedDoctorId;
		if (principal.getRole() == UserRole.PATIENT) {
			patientId = patientRepository.findByUserId(principal.getUserId())
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Patient profile not found")).getId();
			if (requestedPatientId != null && !requestedPatientId.equals(patientId)) {
				throw new AccessDeniedException("You cannot search another patient's appointments");
			}
		} else if (principal.getRole() == UserRole.DOCTOR) {
			doctorId = doctorRepository.findByUserId(principal.getUserId())
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Doctor profile not found")).getId();
			if (requestedDoctorId != null && !requestedDoctorId.equals(doctorId)) {
				throw new AccessDeniedException("You cannot search another doctor's appointments");
			}
		}

		PageRequest pageRequest = PageRequest.of(page, size, Sort.by("startAt").ascending());
		List<Specification<Appointment>> filters = new ArrayList<>();
		if (patientId != null) {
			Long scopedPatientId = patientId;
			filters.add((root, query, criteriaBuilder) ->
				criteriaBuilder.equal(root.get("patient").get("id"), scopedPatientId));
		}
		if (doctorId != null) {
			Long scopedDoctorId = doctorId;
			filters.add((root, query, criteriaBuilder) ->
				criteriaBuilder.equal(root.get("doctor").get("id"), scopedDoctorId));
		}
		if (status != null) {
			filters.add((root, query, criteriaBuilder) ->
				criteriaBuilder.equal(root.get("status"), status));
		}
		if (fromAt != null) {
			filters.add((root, query, criteriaBuilder) ->
				criteriaBuilder.greaterThanOrEqualTo(root.<OffsetDateTime>get("startAt"), fromAt));
		}
		if (toAt != null) {
			filters.add((root, query, criteriaBuilder) ->
				criteriaBuilder.lessThan(root.<OffsetDateTime>get("startAt"), toAt));
		}
		Specification<Appointment> specification = filters.isEmpty()
			? (root, query, criteriaBuilder) -> criteriaBuilder.conjunction()
			: filters.stream().reduce(Specification::and).orElseThrow();
		return appointmentRepository.findAll(specification, pageRequest)
			.map(AppointmentService::toResponse);
	}

	@Transactional(readOnly = true)
	public AppointmentResponse getById(Long id, HospitalUserPrincipal principal) {
		assertAppointmentRole(principal);
		Appointment appointment = findAppointment(id);
		assertCanView(appointment, principal);
		return toResponse(appointment);
	}

	@Transactional
	public AppointmentResponse reschedule(Long id, AppointmentRescheduleRequest request,
			HospitalUserPrincipal principal) {
		assertAppointmentRole(principal);
		Appointment appointment = appointmentRepository.findByIdForUpdate(id)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Appointment not found"));
		assertCanManage(appointment, principal);
		if (appointment.getStatus() != AppointmentStatus.SCHEDULED) {
			throw new ApiException(HttpStatus.CONFLICT, "Only scheduled appointments can be rescheduled");
		}
		assertAppointmentHasNotStarted(appointment, "rescheduled");

		Doctor doctor = doctorRepository.findByIdForUpdate(appointment.getDoctor().getId())
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Doctor not found"));
		Patient patient = patientRepository.findByIdForUpdate(appointment.getPatient().getId())
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Patient not found"));
		assertDoctorIsActive(doctor);
		assertBookingAccess(patient, doctor, principal);

		OffsetDateTime startAt = request.startAt();
		OffsetDateTime endAt = startAt.plusMinutes(request.durationMinutes());
		validateAppointmentTime(doctor.getId(), startAt, endAt, request.durationMinutes());
		assertNoOverlap(patient.getId(), doctor.getId(), startAt, endAt, appointment.getId());
		appointment.reschedule(startAt, endAt);
		return toResponse(appointment);
	}

	@Transactional
	public AppointmentResponse cancel(Long id, HospitalUserPrincipal principal) {
		assertAppointmentRole(principal);
		Appointment appointment = appointmentRepository.findByIdForUpdate(id)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Appointment not found"));
		assertCanManage(appointment, principal);
		if (appointment.getStatus() != AppointmentStatus.SCHEDULED) {
			throw new ApiException(HttpStatus.CONFLICT, "Only scheduled appointments can be cancelled");
		}
		if (!appointment.getStartAt().isAfter(OffsetDateTime.now(clock).plusHours(24))) {
			throw new ApiException(HttpStatus.CONFLICT,
				"Appointments can only be cancelled more than 24 hours before their start time");
		}

		doctorRepository.findByIdForUpdate(appointment.getDoctor().getId())
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Doctor not found"));
		patientRepository.findByIdForUpdate(appointment.getPatient().getId())
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Patient not found"));
		appointment.cancel();
		return toResponse(appointment);
	}

	private void assertAppointmentHasNotStarted(Appointment appointment, String action) {
		if (!appointment.getStartAt().isAfter(OffsetDateTime.now(clock))) {
			throw new ApiException(HttpStatus.CONFLICT,
				"Appointments that have already started cannot be " + action);
		}
	}

	private void validateAppointmentTime(Long doctorId, OffsetDateTime startAt,
			OffsetDateTime endAt, int durationMinutes) {
		if (durationMinutes < 15 || durationMinutes > 120 || durationMinutes % 15 != 0) {
			throw new ApiException(HttpStatus.BAD_REQUEST,
				"Appointment duration must be between 15 and 120 minutes in 15-minute increments");
		}
		OffsetDateTime startInLocalZone = startAt.atZoneSameInstant(hospitalTimeZone).toOffsetDateTime();
		OffsetDateTime endInLocalZone = endAt.atZoneSameInstant(hospitalTimeZone).toOffsetDateTime();
		if (!startInLocalZone.isAfter(OffsetDateTime.now(hospitalTimeZone))) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Appointment start time must be in the future");
		}
		if (!isQuarterHour(startInLocalZone) || !isQuarterHour(endInLocalZone)
				|| !startInLocalZone.toLocalDate().equals(endInLocalZone.toLocalDate())) {
			throw new ApiException(HttpStatus.BAD_REQUEST,
				"Appointment times must use 15-minute increments and remain within one local date");
		}

		LocalDateTime startLocal = startInLocalZone.toLocalDateTime();
		LocalDateTime endLocal = endInLocalZone.toLocalDateTime();
		short dayOfWeek = (short) startInLocalZone.getDayOfWeek().getValue();
		boolean available = availabilityRepository.findByDoctorIdAndDayOfWeek(doctorId, dayOfWeek).stream()
			.anyMatch(slot -> !startLocal.toLocalTime().isBefore(slot.getStartTime())
				&& !endLocal.toLocalTime().isAfter(slot.getEndTime()));
		if (!available) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Appointment time is outside the doctor's availability");
		}
	}

	private void assertNoOverlap(Long patientId, Long doctorId, OffsetDateTime startAt,
			OffsetDateTime endAt, Long excludingId) {
		boolean doctorConflict = appointmentRepository.existsDoctorOverlap(doctorId, startAt,
			endAt, AppointmentStatus.SCHEDULED, excludingId);
		if (doctorConflict) {
			throw new ApiException(HttpStatus.CONFLICT, "Doctor already has an overlapping appointment");
		}
		if (appointmentRepository.existsPatientOverlap(patientId, startAt, endAt,
				AppointmentStatus.SCHEDULED, excludingId)) {
			throw new ApiException(HttpStatus.CONFLICT, "Patient already has an overlapping appointment");
		}
	}

	private Long resolvePatientId(Long requestedPatientId, HospitalUserPrincipal principal) {
		if (principal.getRole() != UserRole.PATIENT) {
			if (requestedPatientId == null) {
				throw new ApiException(HttpStatus.BAD_REQUEST, "patientId is required for staff bookings");
			}
			return requestedPatientId;
		}
		Long ownPatientId = patientRepository.findByUserId(principal.getUserId())
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Patient profile not found")).getId();
		if (requestedPatientId != null && !requestedPatientId.equals(ownPatientId)) {
			throw new AccessDeniedException("You can only book appointments for yourself");
		}
		return ownPatientId;
	}

	private Long resolveDoctorId(Long requestedDoctorId, HospitalUserPrincipal principal) {
		if (principal.getRole() != UserRole.DOCTOR) {
			if (requestedDoctorId == null) {
				throw new ApiException(HttpStatus.BAD_REQUEST, "doctorId is required");
			}
			return requestedDoctorId;
		}
		Long ownDoctorId = doctorRepository.findByUserId(principal.getUserId())
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Doctor profile not found")).getId();
		if (requestedDoctorId != null && !requestedDoctorId.equals(ownDoctorId)) {
			throw new AccessDeniedException("You can only book appointments for your own schedule");
		}
		return ownDoctorId;
	}

	private static void assertDoctorIsActive(Doctor doctor) {
		if (!doctor.getUser().isActive()) {
			throw new ApiException(HttpStatus.CONFLICT, "Doctor is not active for appointments");
		}
	}

	private static void assertBookingAccess(Patient patient, Doctor doctor,
			HospitalUserPrincipal principal) {
		if (principal.getRole() == UserRole.PATIENT
				&& !patient.getUser().getId().equals(principal.getUserId())) {
			throw new AccessDeniedException("You can only book appointments for yourself");
		}
		if (principal.getRole() == UserRole.DOCTOR
				&& !doctor.getUser().getId().equals(principal.getUserId())) {
			throw new AccessDeniedException("You can only book appointments for your own schedule");
		}
	}

	private static void assertCanView(Appointment appointment, HospitalUserPrincipal principal) {
		if (principal.getRole() == UserRole.ADMIN || principal.getRole() == UserRole.RECEPTIONIST) {
			return;
		}
		if (principal.getRole() == UserRole.PATIENT
				&& appointment.getPatient().getUser().getId().equals(principal.getUserId())) {
			return;
		}
		if (principal.getRole() == UserRole.DOCTOR
				&& appointment.getDoctor().getUser().getId().equals(principal.getUserId())) {
			return;
		}
		throw new AccessDeniedException("You cannot access this appointment");
	}

	private static void assertCanManage(Appointment appointment, HospitalUserPrincipal principal) {
		if (principal.getRole() == UserRole.ADMIN || principal.getRole() == UserRole.RECEPTIONIST) {
			return;
		}
		assertCanView(appointment, principal);
	}

	private static void assertAppointmentRole(HospitalUserPrincipal principal) {
		if (!APPOINTMENT_ROLES.contains(principal.getRole())) {
			throw new AccessDeniedException("Your role cannot access appointments");
		}
	}

	private Appointment findAppointment(Long id) {
		return appointmentRepository.findById(id)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Appointment not found"));
	}

	private static boolean isQuarterHour(OffsetDateTime dateTime) {
		return dateTime.getMinute() % 15 == 0 && dateTime.getSecond() == 0 && dateTime.getNano() == 0;
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private static AppointmentResponse toResponse(Appointment appointment) {
		return new AppointmentResponse(appointment.getId(), appointment.getPatient().getId(),
			appointment.getPatient().getMedicalRecordNumber(), appointment.getDoctor().getId(),
			appointment.getDoctor().getFirstName() + " " + appointment.getDoctor().getLastName(),
			appointment.getStartAt(), appointment.getEndAt(), appointment.getStatus(),
			appointment.getReason(), appointment.getCancelledAt());
	}
}