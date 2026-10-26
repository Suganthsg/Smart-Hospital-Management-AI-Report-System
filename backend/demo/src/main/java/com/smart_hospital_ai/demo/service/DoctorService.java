package com.smart_hospital_ai.demo.service;

import java.util.Locale;
import com.smart_hospital_ai.demo.dto.DoctorCreateRequest;
import com.smart_hospital_ai.demo.dto.DoctorResponse;
import com.smart_hospital_ai.demo.dto.DoctorUpdateRequest;
import com.smart_hospital_ai.demo.entity.Doctor;
import com.smart_hospital_ai.demo.entity.UserAccount;
import com.smart_hospital_ai.demo.entity.UserRole;
import com.smart_hospital_ai.demo.exception.ApiException;
import com.smart_hospital_ai.demo.repository.DoctorRepository;
import com.smart_hospital_ai.demo.repository.AppointmentRepository;
import com.smart_hospital_ai.demo.repository.DoctorAvailabilityRepository;
import com.smart_hospital_ai.demo.repository.EncounterRepository;
import com.smart_hospital_ai.demo.repository.PatientRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DoctorService {

	private static final int MAX_PAGE_SIZE = 100;
	private final DoctorRepository doctorRepository;
	private final AppointmentRepository appointmentRepository;
	private final EncounterRepository encounterRepository;
	private final PatientRepository patientRepository;
	private final DoctorAvailabilityRepository availabilityRepository;
	private final UserAccountService userAccountService;

	public DoctorService(DoctorRepository doctorRepository, AppointmentRepository appointmentRepository,
			EncounterRepository encounterRepository, PatientRepository patientRepository,
			DoctorAvailabilityRepository availabilityRepository, UserAccountService userAccountService) {
		this.doctorRepository = doctorRepository;
		this.appointmentRepository = appointmentRepository;
		this.encounterRepository = encounterRepository;
		this.patientRepository = patientRepository;
		this.availabilityRepository = availabilityRepository;
		this.userAccountService = userAccountService;
	}

	@Transactional
	public DoctorResponse create(DoctorCreateRequest request) {
		UserAccount user = userAccountService.create(request.email(), request.password(), UserRole.DOCTOR);
		Doctor doctor = new Doctor(user, request.licenseNumber().trim(), request.firstName().trim(),
			request.lastName().trim(), request.specialization().trim(), blankToNull(request.phone()));
		return toResponse(doctorRepository.save(doctor));
	}

	@Transactional(readOnly = true)
	public Page<DoctorResponse> search(String term, int page, int size) {
		if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Page must be non-negative and size must be between 1 and 100");
		}
		PageRequest pageRequest = PageRequest.of(page, size,
			Sort.by("lastName").ascending().and(Sort.by("firstName").ascending()));
		return doctorRepository.search(term == null ? "" : term.trim().toLowerCase(Locale.ROOT), pageRequest)
			.map(DoctorService::toResponse);
	}

	@Transactional(readOnly = true)
	public DoctorResponse getById(Long id) {
		return toResponse(findDoctor(id));
	}

	@Transactional
	public DoctorResponse update(Long id, DoctorUpdateRequest request) {
		Doctor doctor = findDoctor(id);
		doctor.updateDetails(request.licenseNumber().trim(), request.firstName().trim(), request.lastName().trim(),
			request.specialization().trim(), blankToNull(request.phone()));
		return toResponse(doctor);
	}

	@Transactional
	public void delete(Long id) {
		Doctor doctor = doctorRepository.findByIdForUpdate(id)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Doctor not found"));
		if (appointmentRepository.existsByDoctorId(id)
				|| encounterRepository.existsByDoctorId(id)
				|| patientRepository.existsByAssignedDoctorId(id)
				|| availabilityRepository.existsByDoctorId(id)) {
			throw new ApiException(HttpStatus.CONFLICT,
				"Doctor cannot be deleted because appointments, clinical encounters, assigned patients, or availability are linked to this record. Medical history has been preserved.");
		}

		doctor.getUser().deactivate();
		doctorRepository.delete(doctor);
	}

	Doctor findDoctor(Long id) {
		return doctorRepository.findById(id)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Doctor not found"));
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private static DoctorResponse toResponse(Doctor doctor) {
		return new DoctorResponse(doctor.getId(), doctor.getUser().getEmail(), doctor.getLicenseNumber(),
			doctor.getFirstName(), doctor.getLastName(), doctor.getSpecialization(), doctor.getPhone());
	}
}