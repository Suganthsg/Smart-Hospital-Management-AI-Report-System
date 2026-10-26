package com.smart_hospital_ai.demo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import com.smart_hospital_ai.demo.dto.LabResultCreateRequest;
import com.smart_hospital_ai.demo.entity.Doctor;
import com.smart_hospital_ai.demo.entity.Encounter;
import com.smart_hospital_ai.demo.entity.LabOrder;
import com.smart_hospital_ai.demo.entity.LabOrderStatus;
import com.smart_hospital_ai.demo.entity.LabResult;
import com.smart_hospital_ai.demo.entity.LabResultStatus;
import com.smart_hospital_ai.demo.entity.Patient;
import com.smart_hospital_ai.demo.entity.UserAccount;
import com.smart_hospital_ai.demo.entity.UserRole;
import com.smart_hospital_ai.demo.exception.ApiException;
import com.smart_hospital_ai.demo.repository.DoctorRepository;
import com.smart_hospital_ai.demo.repository.EncounterRepository;
import com.smart_hospital_ai.demo.repository.LabOrderRepository;
import com.smart_hospital_ai.demo.repository.LabResultRepository;
import com.smart_hospital_ai.demo.repository.PatientRepository;
import com.smart_hospital_ai.demo.repository.UserRepository;
import com.smart_hospital_ai.demo.security.HospitalUserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class LabOrderServiceTest {

	private final LabOrderRepository orderRepository = mock(LabOrderRepository.class);
	private final LabResultRepository resultRepository = mock(LabResultRepository.class);
	private final EncounterRepository encounterRepository = mock(EncounterRepository.class);
	private final DoctorRepository doctorRepository = mock(DoctorRepository.class);
	private final PatientRepository patientRepository = mock(PatientRepository.class);
	private final UserRepository userRepository = mock(UserRepository.class);
	private final LabOrderService service = new LabOrderService(orderRepository, resultRepository,
		encounterRepository, doctorRepository, patientRepository, userRepository);
	private final HospitalUserPrincipal technician = mock(HospitalUserPrincipal.class);
	private final HospitalUserPrincipal doctorPrincipal = mock(HospitalUserPrincipal.class);
	private final UserAccount actor = mock(UserAccount.class);
	private final LabOrder order = mock(LabOrder.class);
	private final Encounter encounter = mock(Encounter.class);
	private final Doctor doctor = mock(Doctor.class);
	private final Patient patient = mock(Patient.class);

	@BeforeEach
	void setUp() {
		when(technician.getRole()).thenReturn(UserRole.LAB_TECHNICIAN);
		when(technician.getUserId()).thenReturn(10L);
		when(doctorPrincipal.getRole()).thenReturn(UserRole.DOCTOR);
		when(doctorPrincipal.getUserId()).thenReturn(20L);
		when(userRepository.getReferenceById(10L)).thenReturn(actor);
		when(userRepository.getReferenceById(20L)).thenReturn(actor);
		when(orderRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(order));
		when(order.getId()).thenReturn(30L);
		when(order.getEncounter()).thenReturn(encounter);
		when(order.getStatus()).thenReturn(LabOrderStatus.ORDERED);
		when(encounter.getDoctor()).thenReturn(doctor);
		when(doctor.getUser()).thenReturn(actor);
		when(actor.getId()).thenReturn(20L);
		when(encounter.getPatient()).thenReturn(patient);
		when(patient.getAssignedDoctor()).thenReturn(doctor);
		when(resultRepository.findFirstByLabOrderIdOrderByResultVersionDesc(30L)).thenReturn(Optional.empty());
		when(resultRepository.save(any(LabResult.class))).thenAnswer(invocation -> invocation.getArgument(0));
	}

	@Test
	void entersAppendOnlyResultVersionForTechnician() {
		var response = service.enterResult(30L,
			new LabResultCreateRequest(" 4.2 ", "mmol/L", null, "No range supplied"), technician);

		assertEquals(1, response.resultVersion());
		assertEquals("4.2", response.resultValue());
		assertEquals(LabResultStatus.ENTERED, response.status());
		verify(order).resultEntered();
		verify(resultRepository).save(any(LabResult.class));
	}

	@Test
	void rejectsNonTechnicianResultEntry() {
		assertThrows(AccessDeniedException.class, () -> service.enterResult(30L,
			new LabResultCreateRequest("4.2", null, null, null), doctorPrincipal));
		verify(resultRepository, never()).save(any(LabResult.class));
	}

	@Test
	void verificationRequiresAssignedDoctorAndLatestUnverifiedResult() {
		LabResult result = mock(LabResult.class);
		when(resultRepository.findFirstByLabOrderIdOrderByResultVersionDesc(30L)).thenReturn(Optional.of(result));
		when(result.getId()).thenReturn(40L);
		when(result.getLabOrder()).thenReturn(order);
		when(result.getResultVersion()).thenReturn(1);
		when(result.getEnteredBy()).thenReturn(actor);
		when(result.getVerifiedBy()).thenReturn(actor);
		AtomicReference<LabResultStatus> resultStatus = new AtomicReference<>(LabResultStatus.ENTERED);
		when(result.getStatus()).thenAnswer(invocation -> resultStatus.get());
		doAnswer(invocation -> {
			resultStatus.set(LabResultStatus.VERIFIED);
			return null;
		}).when(result).verify(actor);
		when(resultRepository.findByIdForUpdate(40L)).thenReturn(Optional.of(result));

		var response = service.verifyResult(30L, 40L, doctorPrincipal);

		assertEquals(LabResultStatus.VERIFIED, response.status());
		verify(result).verify(actor);
		verify(order).resultVerified();
	}

	@Test
	void rejectsVerificationOfSupersededResultVersion() {
		LabResult latest = mock(LabResult.class);
		when(latest.getId()).thenReturn(41L);
		when(resultRepository.findFirstByLabOrderIdOrderByResultVersionDesc(30L)).thenReturn(Optional.of(latest));

		assertThrows(ApiException.class, () -> service.verifyResult(30L, 40L, doctorPrincipal));
		verify(resultRepository, never()).findByIdForUpdate(40L);
	}
}