package com.smart_hospital_ai.demo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import com.smart_hospital_ai.demo.entity.Doctor;
import com.smart_hospital_ai.demo.entity.UserAccount;
import com.smart_hospital_ai.demo.exception.ApiException;
import com.smart_hospital_ai.demo.repository.AppointmentRepository;
import com.smart_hospital_ai.demo.repository.DoctorAvailabilityRepository;
import com.smart_hospital_ai.demo.repository.DoctorRepository;
import com.smart_hospital_ai.demo.repository.EncounterRepository;
import com.smart_hospital_ai.demo.repository.PatientRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class DoctorDeletionServiceTest {

	private final DoctorRepository doctorRepository = mock(DoctorRepository.class);
	private final AppointmentRepository appointmentRepository = mock(AppointmentRepository.class);
	private final EncounterRepository encounterRepository = mock(EncounterRepository.class);
	private final PatientRepository patientRepository = mock(PatientRepository.class);
	private final DoctorAvailabilityRepository availabilityRepository = mock(DoctorAvailabilityRepository.class);
	private final Doctor doctor = mock(Doctor.class);
	private final UserAccount doctorAccount = mock(UserAccount.class);
	private final DoctorService service = new DoctorService(doctorRepository, appointmentRepository,
		encounterRepository, patientRepository, availabilityRepository, mock(UserAccountService.class));

	@Test
	void deletesDoctorProfileAndDeactivatesAccountWhenNoRecordsAreLinked() {
		when(doctorRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(doctor));
		when(doctor.getUser()).thenReturn(doctorAccount);
		when(appointmentRepository.existsByDoctorId(7L)).thenReturn(false);
		when(encounterRepository.existsByDoctorId(7L)).thenReturn(false);
		when(patientRepository.existsByAssignedDoctorId(7L)).thenReturn(false);
		when(availabilityRepository.existsByDoctorId(7L)).thenReturn(false);

		service.delete(7L);

		verify(doctorRepository).delete(doctor);
		verify(doctorAccount).deactivate();
	}

	@Test
	void rejectsDoctorDeletionWhenClinicalEncountersExist() {
		when(doctorRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(doctor));
		when(appointmentRepository.existsByDoctorId(7L)).thenReturn(false);
		when(encounterRepository.existsByDoctorId(7L)).thenReturn(true);

		ApiException exception = assertThrows(ApiException.class, () -> service.delete(7L));

		assertEquals(HttpStatus.CONFLICT, exception.getStatus());
		assertEquals("Doctor cannot be deleted because appointments, clinical encounters, assigned patients, or availability are linked to this record. Medical history has been preserved.",
			exception.getMessage());
		verify(doctorRepository, never()).delete(doctor);
		verify(doctorAccount, never()).deactivate();
	}

	@Test
	void missingDoctorReturnsNotFound() {
		when(doctorRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

		ApiException exception = assertThrows(ApiException.class, () -> service.delete(99L));

		assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
		verify(appointmentRepository, never()).existsByDoctorId(99L);
	}
}
