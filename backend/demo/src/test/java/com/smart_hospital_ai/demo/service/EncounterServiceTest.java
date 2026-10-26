package com.smart_hospital_ai.demo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;
import com.smart_hospital_ai.demo.dto.EncounterCreateRequest;
import com.smart_hospital_ai.demo.dto.EncounterUpdateRequest;
import com.smart_hospital_ai.demo.entity.Appointment;
import com.smart_hospital_ai.demo.entity.AppointmentStatus;
import com.smart_hospital_ai.demo.entity.Doctor;
import com.smart_hospital_ai.demo.entity.Encounter;
import com.smart_hospital_ai.demo.entity.EncounterStatus;
import com.smart_hospital_ai.demo.entity.Patient;
import com.smart_hospital_ai.demo.entity.UserAccount;
import com.smart_hospital_ai.demo.entity.UserRole;
import com.smart_hospital_ai.demo.exception.ApiException;
import com.smart_hospital_ai.demo.repository.AppointmentRepository;
import com.smart_hospital_ai.demo.repository.DoctorRepository;
import com.smart_hospital_ai.demo.repository.EncounterRepository;
import com.smart_hospital_ai.demo.repository.PatientRepository;
import com.smart_hospital_ai.demo.repository.UserRepository;
import com.smart_hospital_ai.demo.security.HospitalUserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class EncounterServiceTest {

	private final EncounterRepository encounterRepository = mock(EncounterRepository.class);
	private final PatientRepository patientRepository = mock(PatientRepository.class);
	private final DoctorRepository doctorRepository = mock(DoctorRepository.class);
	private final AppointmentRepository appointmentRepository = mock(AppointmentRepository.class);
	private final UserRepository userRepository = mock(UserRepository.class);
	private final EncounterService service = new EncounterService(encounterRepository, patientRepository,
		doctorRepository, appointmentRepository, userRepository);
	private final HospitalUserPrincipal principal = mock(HospitalUserPrincipal.class);
	private final Patient patient = mock(Patient.class);
	private final Doctor doctor = mock(Doctor.class);
	private final UserAccount author = mock(UserAccount.class);
	private final LocalDate encounterDate = LocalDate.now();

	@BeforeEach
	void setUp() {
		when(principal.getRole()).thenReturn(UserRole.DOCTOR);
		when(principal.getUserId()).thenReturn(7L);
		when(doctorRepository.findByUserId(7L)).thenReturn(Optional.of(doctor));
		when(doctorRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(doctor));
		when(doctor.getId()).thenReturn(11L);
		when(doctor.getUser()).thenReturn(author);
		when(author.getId()).thenReturn(7L);
		when(patientRepository.findByIdForUpdate(13L)).thenReturn(Optional.of(patient));
		when(patient.getId()).thenReturn(13L);
		when(patient.getAssignedDoctor()).thenReturn(doctor);
		when(userRepository.getReferenceById(7L)).thenReturn(author);
		when(encounterRepository.save(any(Encounter.class))).thenAnswer(invocation -> invocation.getArgument(0));
	}

	@Test
	void createsDraftForCurrentlyAssignedPatient() {
		var response = service.create(new EncounterCreateRequest(13L, null, encounterDate,
			" Clinical notes ", " Diagnosis "), principal);

		assertEquals(EncounterStatus.DRAFT, response.status());
		assertEquals(13L, response.patientId());
		verify(encounterRepository).save(any(Encounter.class));
	}

	@Test
	void responseKeepsEncounterIdDistinctFromPatientId() {
		Encounter encounter = mock(Encounter.class);
		when(principal.getRole()).thenReturn(UserRole.ADMIN);
		when(encounterRepository.findById(98L)).thenReturn(Optional.of(encounter));
		when(encounter.getId()).thenReturn(98L);
		when(encounter.getPatient()).thenReturn(patient);
		when(patient.getId()).thenReturn(13L);
		when(patient.getMedicalRecordNumber()).thenReturn("MRN-13");
		when(encounter.getDoctor()).thenReturn(doctor);
		when(doctor.getId()).thenReturn(11L);
		when(doctor.getFirstName()).thenReturn("Assigned");
		when(doctor.getLastName()).thenReturn("Doctor");
		when(encounter.getCreatedBy()).thenReturn(author);
		when(author.getId()).thenReturn(7L);
		when(encounter.getEncounterDate()).thenReturn(encounterDate);
		when(encounter.getClinicalNotes()).thenReturn("Notes");
		when(encounter.getDiagnosis()).thenReturn("Diagnosis");
		when(encounter.getStatus()).thenReturn(EncounterStatus.DRAFT);

		var response = service.getById(98L, principal);

		assertEquals(98L, response.id());
		assertEquals(13L, response.patientId());
	}

	@Test
	void rejectsPatientNotAssignedToDoctor() {
		when(patient.getAssignedDoctor()).thenReturn(null);

		assertThrows(AccessDeniedException.class, () -> service.create(
			new EncounterCreateRequest(13L, null, encounterDate, "notes", "diagnosis"), principal));
		verify(encounterRepository, never()).save(any(Encounter.class));
	}

	@Test
	void rejectsAppointmentForDifferentPatientOrDoctor() {
		Appointment appointment = mock(Appointment.class);
		Patient otherPatient = mock(Patient.class);
		when(appointmentRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(appointment));
		when(appointment.getPatient()).thenReturn(otherPatient);
		when(otherPatient.getId()).thenReturn(99L);
		when(appointment.getDoctor()).thenReturn(doctor);

		ApiException exception = assertThrows(ApiException.class, () -> service.create(
			new EncounterCreateRequest(13L, 20L, encounterDate, "notes", "diagnosis"), principal));

		assertEquals(400, exception.getStatus().value());
		verify(encounterRepository, never()).save(any(Encounter.class));
	}

	@Test
	void finalizedEncounterCannotBeUpdated() {
		Encounter encounter = mock(Encounter.class);
		when(encounterRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(encounter));
		when(encounter.getPatient()).thenReturn(patient);
		when(encounter.getDoctor()).thenReturn(doctor);
		when(encounter.getStatus()).thenReturn(EncounterStatus.FINALIZED);

		ApiException exception = assertThrows(ApiException.class, () -> service.update(30L,
			new EncounterUpdateRequest(encounterDate, "notes", "diagnosis", EncounterStatus.FINALIZED), principal));

		assertEquals(409, exception.getStatus().value());
		verify(encounter, never()).update(any(), any(), any(), any());
	}
}