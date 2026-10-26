package com.smart_hospital_ai.demo.service;

import java.util.Locale;
import java.util.UUID;
import com.smart_hospital_ai.demo.dto.PatientRegistrationRequest;
import com.smart_hospital_ai.demo.dto.PatientResponse;
import com.smart_hospital_ai.demo.dto.PatientUpdateRequest;
import com.smart_hospital_ai.demo.entity.Doctor;
import com.smart_hospital_ai.demo.entity.Patient;
import com.smart_hospital_ai.demo.entity.UserAccount;
import com.smart_hospital_ai.demo.entity.UserRole;
import com.smart_hospital_ai.demo.exception.ApiException;
import com.smart_hospital_ai.demo.repository.PatientRepository;
import com.smart_hospital_ai.demo.repository.DoctorRepository;
import com.smart_hospital_ai.demo.repository.AppointmentRepository;
import com.smart_hospital_ai.demo.repository.EncounterRepository;
import com.smart_hospital_ai.demo.security.HospitalUserPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PatientService {

	private static final int MAX_PAGE_SIZE = 100;
	private final PatientRepository patientRepository;
	private final DoctorRepository doctorRepository;
	private final AppointmentRepository appointmentRepository;
	private final EncounterRepository encounterRepository;
	private final UserAccountService userAccountService;

	public PatientService(PatientRepository patientRepository, DoctorRepository doctorRepository,
			AppointmentRepository appointmentRepository, EncounterRepository encounterRepository,
			UserAccountService userAccountService) {
		this.patientRepository = patientRepository;
		this.doctorRepository = doctorRepository;
		this.appointmentRepository = appointmentRepository;
		this.encounterRepository = encounterRepository;
		this.userAccountService = userAccountService;
	}

	@Transactional
	public PatientResponse register(PatientRegistrationRequest request) {
		UserAccount user = userAccountService.create(request.email(), request.password(), UserRole.PATIENT);
		String medicalRecordNumber = "MRN-" + UUID.randomUUID().toString().replace("-", "")
			.toUpperCase(Locale.ROOT);
		Patient patient = new Patient(user, medicalRecordNumber, request.firstName().trim(),
			request.lastName().trim(), request.dateOfBirth(), blankToNull(request.phone()));
		return toResponse(patientRepository.save(patient));
	}

	@Transactional(readOnly = true)
	public Page<PatientResponse> search(String term, int page, int size) {
		PageRequest pageRequest = pageRequest(page, size);
		return patientRepository.search(normalizeTerm(term), pageRequest).map(PatientService::toResponse);
	}

	@Transactional(readOnly = true)
	public PatientResponse getById(Long id, HospitalUserPrincipal principal) {
		Patient patient = patientRepository.findById(id)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Patient not found"));
		assertCanRead(patient, principal);
		return toResponse(patient);
	}

	@Transactional
	public PatientResponse assignDoctor(Long patientId, Long doctorId) {
		Patient patient = patientRepository.findById(patientId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Patient not found"));
		Doctor doctor = doctorRepository.findById(doctorId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Doctor not found"));
		patient.assignDoctor(doctor);
		return toResponse(patient);
	}

	@Transactional
	public void delete(Long id) {
		Patient patient = patientRepository.findByIdForUpdate(id)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Patient not found"));
		if (appointmentRepository.existsByPatientId(id) || encounterRepository.existsByPatientId(id)) {
			throw new ApiException(HttpStatus.CONFLICT,
				"Patient cannot be deleted because appointments or clinical encounters are linked to this record, including any prescriptions, laboratory records, or reports. Medical history has been preserved.");
		}

		patient.getUser().deactivate();
		patientRepository.delete(patient);
	}

	@Transactional
	public PatientResponse update(Long id, PatientUpdateRequest request, HospitalUserPrincipal principal) {
		Patient patient = patientRepository.findById(id)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Patient not found"));
		boolean owner = principal.getRole() == UserRole.PATIENT
			&& patient.getUser().getId().equals(principal.getUserId());
		boolean staffEditor = principal.getRole() == UserRole.ADMIN
			|| principal.getRole() == UserRole.RECEPTIONIST;
		if (!owner && !staffEditor) {
			throw new AccessDeniedException("You cannot update this patient record");
		}
		patient.updateDetails(request.firstName().trim(), request.lastName().trim(),
			request.dateOfBirth(), blankToNull(request.phone()));
		return toResponse(patient);
	}

	private static PageRequest pageRequest(int page, int size) {
		if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Page must be non-negative and size must be between 1 and 100");
		}
		return PageRequest.of(page, size, Sort.by("lastName").ascending().and(Sort.by("firstName").ascending()));
	}

	private static String normalizeTerm(String term) {
		return term == null ? "" : term.trim().toLowerCase(Locale.ROOT);
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private static void assertCanRead(Patient patient, HospitalUserPrincipal principal) {
		boolean owner = principal.getRole() == UserRole.PATIENT
			&& patient.getUser().getId().equals(principal.getUserId());
		boolean assignedDoctor = principal.getRole() == UserRole.DOCTOR
			&& patient.getAssignedDoctor() != null
			&& patient.getAssignedDoctor().getUser().getId().equals(principal.getUserId());
		boolean staff = principal.getRole() == UserRole.ADMIN || principal.getRole() == UserRole.RECEPTIONIST;
		if (!owner && !assignedDoctor && !staff) {
			throw new AccessDeniedException("You cannot access this patient record");
		}
	}

	private static PatientResponse toResponse(Patient patient) {
		return new PatientResponse(patient.getId(), patient.getMedicalRecordNumber(), patient.getUser().getEmail(),
			patient.getFirstName(), patient.getLastName(), patient.getDateOfBirth(), patient.getPhone(),
			patient.getAssignedDoctor() == null ? null : patient.getAssignedDoctor().getId());
	}
}