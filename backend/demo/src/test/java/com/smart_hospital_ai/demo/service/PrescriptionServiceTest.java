package com.smart_hospital_ai.demo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import com.smart_hospital_ai.demo.dto.PrescriptionCreateRequest;
import com.smart_hospital_ai.demo.dto.PrescriptionItemRequest;
import com.smart_hospital_ai.demo.dto.PrescriptionUpdateRequest;
import com.smart_hospital_ai.demo.entity.Doctor;
import com.smart_hospital_ai.demo.entity.Encounter;
import com.smart_hospital_ai.demo.entity.EncounterStatus;
import com.smart_hospital_ai.demo.entity.Patient;
import com.smart_hospital_ai.demo.entity.Prescription;
import com.smart_hospital_ai.demo.entity.PrescriptionHistory;
import com.smart_hospital_ai.demo.entity.PrescriptionItem;
import com.smart_hospital_ai.demo.entity.PrescriptionStatus;
import com.smart_hospital_ai.demo.entity.UserAccount;
import com.smart_hospital_ai.demo.entity.UserRole;
import com.smart_hospital_ai.demo.exception.ApiException;
import com.smart_hospital_ai.demo.repository.EncounterRepository;
import com.smart_hospital_ai.demo.repository.PrescriptionHistoryItemRepository;
import com.smart_hospital_ai.demo.repository.PrescriptionHistoryRepository;
import com.smart_hospital_ai.demo.repository.PrescriptionItemRepository;
import com.smart_hospital_ai.demo.repository.PrescriptionRepository;
import com.smart_hospital_ai.demo.repository.UserRepository;
import com.smart_hospital_ai.demo.security.HospitalUserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class PrescriptionServiceTest {

	private final PrescriptionRepository prescriptionRepository = mock(PrescriptionRepository.class);
	private final PrescriptionItemRepository itemRepository = mock(PrescriptionItemRepository.class);
	private final PrescriptionHistoryRepository historyRepository = mock(PrescriptionHistoryRepository.class);
	private final PrescriptionHistoryItemRepository historyItemRepository = mock(PrescriptionHistoryItemRepository.class);
	private final EncounterRepository encounterRepository = mock(EncounterRepository.class);
	private final UserRepository userRepository = mock(UserRepository.class);
	private final PrescriptionService service = new PrescriptionService(prescriptionRepository, itemRepository,
		historyRepository, historyItemRepository, encounterRepository, userRepository);
	private final Encounter encounter = mock(Encounter.class);
	private final Doctor doctor = mock(Doctor.class);
	private final Patient patient = mock(Patient.class);
	private final UserAccount user = mock(UserAccount.class);
	private final HospitalUserPrincipal principal = mock(HospitalUserPrincipal.class);

	@BeforeEach
	void setUp() {
		when(principal.getRole()).thenReturn(UserRole.DOCTOR);
		when(principal.getUserId()).thenReturn(4L);
		when(encounter.getId()).thenReturn(8L);
		when(encounter.getDoctor()).thenReturn(doctor);
		when(encounter.getPatient()).thenReturn(patient);
		when(encounter.getStatus()).thenReturn(EncounterStatus.FINALIZED);
		when(doctor.getUser()).thenReturn(user);
		when(user.getId()).thenReturn(4L);
		when(patient.getAssignedDoctor()).thenReturn(doctor);
		when(encounterRepository.findByIdForUpdate(8L)).thenReturn(Optional.of(encounter));
		when(userRepository.getReferenceById(4L)).thenReturn(user);
		when(prescriptionRepository.save(any(Prescription.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(itemRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
		when(historyRepository.save(any(PrescriptionHistory.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(historyItemRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
	}

	@Test
	void createsPrescriptionAndCapturesItemHistory() {
		var response = service.create(8L, new PrescriptionCreateRequest(List.of(item("Drug", 3))), principal);

		assertEquals(PrescriptionStatus.DRAFT, response.status());
		assertEquals(1, response.items().size());
		verify(historyRepository).save(any(PrescriptionHistory.class));
		verify(historyItemRepository).saveAll(any());
	}

	@Test
	void readsPrescriptionsByActualEncounterIdInsteadOfPatientId() {
		when(encounterRepository.findById(98L)).thenReturn(Optional.of(encounter));
		when(encounter.getId()).thenReturn(98L);
		when(patient.getId()).thenReturn(13L);

		assertEquals(List.of(), service.getForEncounter(98L, principal));

		verify(encounterRepository).findById(98L);
		verify(prescriptionRepository).findByEncounterIdOrderByCreatedAtDescIdDesc(98L);
		verify(prescriptionRepository, never()).findByEncounterIdOrderByCreatedAtDescIdDesc(13L);
	}

	@Test
	void rejectsPrescriptionReadWhenDoctorIsNotAuthorizedForEncounter() {
		when(encounterRepository.findById(98L)).thenReturn(Optional.of(encounter));
		when(principal.getUserId()).thenReturn(99L);

		assertThrows(AccessDeniedException.class, () -> service.getForEncounter(98L, principal));

		verify(prescriptionRepository, never()).findByEncounterIdOrderByCreatedAtDescIdDesc(98L);
	}

	@Test
	void reportsMissingEncounterBeforeQueryingPrescriptions() {
		when(encounterRepository.findById(98L)).thenReturn(Optional.empty());

		ApiException exception = assertThrows(ApiException.class, () -> service.getForEncounter(98L, principal));

		assertEquals(404, exception.getStatus().value());
		verify(prescriptionRepository, never()).findByEncounterIdOrderByCreatedAtDescIdDesc(98L);
	}

	@Test
	void rejectsPrescriptionsForDraftEncounters() {
		when(encounter.getStatus()).thenReturn(EncounterStatus.DRAFT);

		assertThrows(ApiException.class, () -> service.create(8L,
			new PrescriptionCreateRequest(List.of(item("Drug", 3))), principal));
		verify(prescriptionRepository, never()).save(any(Prescription.class));
	}

	@Test
	void rejectsReceptionistPrescriptionCreation() {
		when(principal.getRole()).thenReturn(UserRole.RECEPTIONIST);

		assertThrows(AccessDeniedException.class, () -> service.create(8L,
			new PrescriptionCreateRequest(List.of(item("Drug", 3))), principal));
	}

	@Test
	void finalizedPrescriptionCannotBeModified() {
		Prescription prescription = mock(Prescription.class);
		when(prescriptionRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(prescription));
		when(prescription.getEncounter()).thenReturn(encounter);
		when(prescription.getStatus()).thenReturn(PrescriptionStatus.FINALIZED);

		assertThrows(ApiException.class, () -> service.update(12L,
			new PrescriptionUpdateRequest(List.of(item("Changed", 1))), principal));
		verify(itemRepository, never()).deleteByPrescriptionId(12L);
	}

	@Test
	void finalizationRequiresAtLeastOneItem() {
		Prescription prescription = mock(Prescription.class);
		when(prescriptionRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(prescription));
		when(prescription.getEncounter()).thenReturn(encounter);
		when(prescription.getStatus()).thenReturn(PrescriptionStatus.DRAFT);
		when(itemRepository.findByPrescriptionIdOrderByItemOrderAsc(12L)).thenReturn(List.of());

		assertThrows(ApiException.class, () -> service.finalizePrescription(12L, principal));
	}

	private static PrescriptionItemRequest item(String name, int quantity) {
		return new PrescriptionItemRequest(name, "5 mg", "oral", "once daily", "3 days", quantity, null);
	}
}