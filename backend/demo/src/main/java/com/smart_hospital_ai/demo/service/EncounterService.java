package com.smart_hospital_ai.demo.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import com.smart_hospital_ai.demo.dto.EncounterCreateRequest;
import com.smart_hospital_ai.demo.dto.EncounterResponse;
import com.smart_hospital_ai.demo.dto.EncounterUpdateRequest;
import com.smart_hospital_ai.demo.entity.Appointment;
import com.smart_hospital_ai.demo.entity.AppointmentStatus;
import com.smart_hospital_ai.demo.entity.Doctor;
import com.smart_hospital_ai.demo.entity.Encounter;
import com.smart_hospital_ai.demo.entity.EncounterStatus;
import com.smart_hospital_ai.demo.entity.Patient;
import com.smart_hospital_ai.demo.entity.UserAccount;
import com.smart_hospital_ai.demo.entity.UserRole;
import com.smart_hospital_ai.demo.entity.Patient;
import com.smart_hospital_ai.demo.exception.ApiException;
import com.smart_hospital_ai.demo.repository.AppointmentRepository;
import com.smart_hospital_ai.demo.repository.DoctorRepository;
import com.smart_hospital_ai.demo.repository.EncounterRepository;
import com.smart_hospital_ai.demo.repository.PatientRepository;
import com.smart_hospital_ai.demo.repository.UserRepository;
import com.smart_hospital_ai.demo.security.HospitalUserPrincipal;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EncounterService {

	private static final int MAX_PAGE_SIZE = 100;
	private final EncounterRepository encounterRepository;
	private final PatientRepository patientRepository;
	private final DoctorRepository doctorRepository;
	private final AppointmentRepository appointmentRepository;
	private final UserRepository userRepository;

	public EncounterService(EncounterRepository encounterRepository, PatientRepository patientRepository,
			DoctorRepository doctorRepository, AppointmentRepository appointmentRepository,
			UserRepository userRepository) {
		this.encounterRepository = encounterRepository;
		this.patientRepository = patientRepository;
		this.doctorRepository = doctorRepository;
		this.appointmentRepository = appointmentRepository;
		this.userRepository = userRepository;
	}

	@Transactional
	public EncounterResponse create(EncounterCreateRequest request, HospitalUserPrincipal principal) {
		assertDoctor(principal);
		Long doctorId = doctorRepository.findByUserId(principal.getUserId())
			.map(Doctor::getId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Doctor profile not found"));
		Appointment appointment = request.appointmentId() == null ? null
			: appointmentRepository.findByIdForUpdate(request.appointmentId())
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Appointment not found"));

		Doctor doctor = doctorRepository.findByIdForUpdate(doctorId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Doctor profile not found"));
		Patient patient = patientRepository.findByIdForUpdate(request.patientId())
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Patient not found"));
		assertAssignedDoctor(patient, doctor);
		validateEncounterDate(request.encounterDate());

		if (appointment != null) {
			if (!appointment.getPatient().getId().equals(patient.getId())
					|| !appointment.getDoctor().getId().equals(doctor.getId())) {
				throw new ApiException(HttpStatus.BAD_REQUEST,
					"Appointment must belong to this patient and doctor");
			}
			if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
				throw new ApiException(HttpStatus.CONFLICT, "A cancelled appointment cannot have an encounter");
			}
			if (!appointment.getStartAt().toLocalDate().equals(request.encounterDate())) {
				throw new ApiException(HttpStatus.BAD_REQUEST,
					"Encounter date must match the linked appointment date");
			}
		}

		UserAccount author = userRepository.getReferenceById(principal.getUserId());
		Encounter encounter = new Encounter(patient, doctor, appointment, author, request.encounterDate(),
			normalizeText(request.clinicalNotes()), normalizeText(request.diagnosis()));
		return toResponse(encounterRepository.save(encounter));
	}

	@Transactional(readOnly = true)
	public EncounterResponse getById(Long id, HospitalUserPrincipal principal) {
		Encounter encounter = encounterRepository.findById(id)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Encounter not found"));
		assertCanRead(encounter, principal);
		return toResponse(encounter);
	}

	@Transactional(readOnly = true)
	public Page<EncounterResponse> search(EncounterStatus status, LocalDate fromDate, LocalDate toDate,
			Long requestedPatientId, Long requestedDoctorId, int page, int size,
			HospitalUserPrincipal principal) {
		if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Page must be non-negative and size must be between 1 and 100");
		}
		if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "fromDate must not be after toDate");
		}

		Long patientId = requestedPatientId;
		Long doctorId = requestedDoctorId;
		boolean requireCurrentAssignment = false;
		if (principal.getRole() == UserRole.PATIENT) {
			patientId = patientRepository.findByUserId(principal.getUserId())
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Patient profile not found")).getId();
			if (requestedPatientId != null && !requestedPatientId.equals(patientId)) {
				throw new AccessDeniedException("You cannot search another patient's encounters");
			}
		} else if (principal.getRole() == UserRole.DOCTOR) {
			doctorId = doctorRepository.findByUserId(principal.getUserId())
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Doctor profile not found")).getId();
			if (requestedDoctorId != null && !requestedDoctorId.equals(doctorId)) {
				throw new AccessDeniedException("You cannot search another doctor's encounters");
			}
			requireCurrentAssignment = true;
		} else if (principal.getRole() != UserRole.ADMIN) {
			throw new AccessDeniedException("Your role cannot access clinical encounters");
		}

		PageRequest pageable = PageRequest.of(page, size,
			Sort.by("encounterDate").descending().and(Sort.by("createdAt").descending()));
		Long scopedPatientId = patientId;
		Long scopedDoctorId = doctorId;
		boolean scopedCurrentAssignment = requireCurrentAssignment;
		Specification<Encounter> specification = (root, query, criteriaBuilder) -> {
			List<Predicate> predicates = new ArrayList<>();
			if (scopedPatientId != null) {
				predicates.add(criteriaBuilder.equal(root.get("patient").get("id"), scopedPatientId));
			}
			if (scopedDoctorId != null) {
				predicates.add(criteriaBuilder.equal(root.get("doctor").get("id"), scopedDoctorId));
			}
			if (status != null) {
				predicates.add(criteriaBuilder.equal(root.get("status"), status));
			}
			if (fromDate != null) {
				predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("encounterDate"), fromDate));
			}
			if (toDate != null) {
				predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("encounterDate"), toDate));
			}
			if (scopedCurrentAssignment) {
				Join<Encounter, Patient> patient = root.join("patient");
				Join<Patient, Doctor> assignedDoctor = patient.join("assignedDoctor", JoinType.LEFT);
				predicates.add(criteriaBuilder.equal(assignedDoctor.get("id"), root.get("doctor").get("id")));
			}
			return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
		};
		return encounterRepository.findAll(specification, pageable).map(EncounterService::toResponse);
	}

	@Transactional
	public EncounterResponse update(Long id, EncounterUpdateRequest request, HospitalUserPrincipal principal) {
		assertDoctor(principal);
		Encounter encounter = encounterRepository.findByIdForUpdate(id)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Encounter not found"));
		assertAssignedDoctor(encounter.getPatient(), encounter.getDoctor());
		if (!encounter.getDoctor().getUser().getId().equals(principal.getUserId())) {
			throw new AccessDeniedException("Only the assigned doctor can update this encounter");
		}
		if (encounter.getStatus() == EncounterStatus.FINALIZED) {
			throw new ApiException(HttpStatus.CONFLICT, "Finalized encounters cannot be changed");
		}
		validateEncounterDate(request.encounterDate());
		encounter.update(request.encounterDate(), normalizeText(request.clinicalNotes()),
			normalizeText(request.diagnosis()), request.status());
		return toResponse(encounter);
	}

	private static void assertDoctor(HospitalUserPrincipal principal) {
		if (principal.getRole() != UserRole.DOCTOR) {
			throw new AccessDeniedException("Only doctors can create or update clinical encounters");
		}
	}

	private static void assertAssignedDoctor(Patient patient, Doctor doctor) {
		if (patient.getAssignedDoctor() == null
				|| !patient.getAssignedDoctor().getId().equals(doctor.getId())) {
			throw new AccessDeniedException("Patient is not assigned to this doctor");
		}
	}

	private static void assertCanRead(Encounter encounter, HospitalUserPrincipal principal) {
		if (principal.getRole() == UserRole.ADMIN) {
			return;
		}
		if (principal.getRole() == UserRole.PATIENT
				&& encounter.getPatient().getUser().getId().equals(principal.getUserId())) {
			return;
		}
		if (principal.getRole() == UserRole.DOCTOR
				&& encounter.getDoctor().getUser().getId().equals(principal.getUserId())
				&& encounter.getPatient().getAssignedDoctor() != null
				&& encounter.getPatient().getAssignedDoctor().getId().equals(encounter.getDoctor().getId())) {
			return;
		}
		throw new AccessDeniedException("You cannot access this clinical encounter");
	}

	private static void validateEncounterDate(LocalDate encounterDate) {
		if (encounterDate.isAfter(LocalDate.now())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Encounter date cannot be in the future");
		}
	}

	private static String normalizeText(String value) {
		return value.trim();
	}

	private static EncounterResponse toResponse(Encounter encounter) {
		return new EncounterResponse(encounter.getId(), encounter.getPatient().getId(),
			encounter.getPatient().getMedicalRecordNumber(), encounter.getDoctor().getId(),
			encounter.getDoctor().getFirstName() + " " + encounter.getDoctor().getLastName(),
			encounter.getAppointment() == null ? null : encounter.getAppointment().getId(),
			encounter.getEncounterDate(), encounter.getClinicalNotes(), encounter.getDiagnosis(),
			encounter.getStatus(), encounter.getCreatedBy().getId(), encounter.getCreatedAt(), encounter.getUpdatedAt());
	}
}