package com.smart_hospital_ai.demo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import com.smart_hospital_ai.demo.entity.Patient;
import com.smart_hospital_ai.demo.entity.UserAccount;
import com.smart_hospital_ai.demo.exception.ApiException;
import com.smart_hospital_ai.demo.repository.AppointmentRepository;
import com.smart_hospital_ai.demo.repository.DoctorRepository;
import com.smart_hospital_ai.demo.repository.EncounterRepository;
import com.smart_hospital_ai.demo.repository.PatientRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class PatientDeletionServiceTest {

	private final PatientRepository patientRepository = mock(PatientRepository.class);
	private final AppointmentRepository appointmentRepository = mock(AppointmentRepository.class);
	private final EncounterRepository encounterRepository = mock(EncounterRepository.class);
	private final Patient patient = mock(Patient.class);
	private final UserAccount patientAccount = mock(UserAccount.class);
	private final PatientService service = new PatientService(patientRepository, mock(DoctorRepository.class),
		appointmentRepository, encounterRepository, mock(UserAccountService.class));

	@Test
	void deletesPatientProfileAndDeactivatesAccountWhenNoMedicalRecordsAreLinked() {
		when(patientRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(patient));
		when(patient.getUser()).thenReturn(patientAccount);
		when(appointmentRepository.existsByPatientId(7L)).thenReturn(false);
		when(encounterRepository.existsByPatientId(7L)).thenReturn(false);

		service.delete(7L);

		verify(patientRepository).delete(patient);
		verify(patientAccount).deactivate();
	}

	@Test
	void rejectsPatientDeletionWhenAppointmentsExist() {
		when(patientRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(patient));
		when(appointmentRepository.existsByPatientId(7L)).thenReturn(true);

		ApiException exception = assertThrows(ApiException.class, () -> service.delete(7L));

		assertEquals(HttpStatus.CONFLICT, exception.getStatus());
		assertEquals("Patient cannot be deleted because appointments or clinical encounters are linked to this record, including any prescriptions, laboratory records, or reports. Medical history has been preserved.",
			exception.getMessage());
		verify(patientRepository, never()).delete(patient);
		verify(patientAccount, never()).deactivate();
	}

	@Test
	void rejectsPatientDeletionWhenClinicalRecordsExist() {
		when(patientRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(patient));
		when(appointmentRepository.existsByPatientId(7L)).thenReturn(false);
		when(encounterRepository.existsByPatientId(7L)).thenReturn(true);

		ApiException exception = assertThrows(ApiException.class, () -> service.delete(7L));

		assertEquals(HttpStatus.CONFLICT, exception.getStatus());
		verify(patientRepository, never()).delete(patient);
		verify(patientAccount, never()).deactivate();
	}

	@Test
	void missingPatientReturnsNotFound() {
		when(patientRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

		ApiException exception = assertThrows(ApiException.class, () -> service.delete(99L));

		assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
		verify(appointmentRepository, never()).existsByPatientId(99L);
	}
}
