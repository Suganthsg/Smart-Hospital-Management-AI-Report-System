package com.smart_hospital_ai.demo.service;

import java.util.List;
import com.smart_hospital_ai.demo.dto.PrescriptionCreateRequest;
import com.smart_hospital_ai.demo.dto.PrescriptionHistoryItemResponse;
import com.smart_hospital_ai.demo.dto.PrescriptionHistoryResponse;
import com.smart_hospital_ai.demo.dto.PrescriptionItemRequest;
import com.smart_hospital_ai.demo.dto.PrescriptionItemResponse;
import com.smart_hospital_ai.demo.dto.PrescriptionResponse;
import com.smart_hospital_ai.demo.dto.PrescriptionUpdateRequest;
import com.smart_hospital_ai.demo.entity.Encounter;
import com.smart_hospital_ai.demo.entity.EncounterStatus;
import com.smart_hospital_ai.demo.entity.Prescription;
import com.smart_hospital_ai.demo.entity.PrescriptionHistory;
import com.smart_hospital_ai.demo.entity.PrescriptionHistoryEventType;
import com.smart_hospital_ai.demo.entity.PrescriptionHistoryItem;
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
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PrescriptionService {

	private final PrescriptionRepository prescriptionRepository;
	private final PrescriptionItemRepository itemRepository;
	private final PrescriptionHistoryRepository historyRepository;
	private final PrescriptionHistoryItemRepository historyItemRepository;
	private final EncounterRepository encounterRepository;
	private final UserRepository userRepository;

	public PrescriptionService(PrescriptionRepository prescriptionRepository,
			PrescriptionItemRepository itemRepository,
			PrescriptionHistoryRepository historyRepository,
			PrescriptionHistoryItemRepository historyItemRepository,
			EncounterRepository encounterRepository, UserRepository userRepository) {
		this.prescriptionRepository = prescriptionRepository;
		this.itemRepository = itemRepository;
		this.historyRepository = historyRepository;
		this.historyItemRepository = historyItemRepository;
		this.encounterRepository = encounterRepository;
		this.userRepository = userRepository;
	}

	@Transactional
	public PrescriptionResponse create(Long encounterId, PrescriptionCreateRequest request,
			HospitalUserPrincipal principal) {
		Encounter encounter = encounterRepository.findByIdForUpdate(encounterId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Encounter not found"));
		assertAssignedDoctor(encounter, principal);
		if (encounter.getStatus() != EncounterStatus.FINALIZED) {
			throw new ApiException(HttpStatus.CONFLICT, "Prescriptions require a finalized encounter");
		}

		UserAccount clinician = userRepository.getReferenceById(principal.getUserId());
		Prescription prescription = prescriptionRepository.save(new Prescription(encounter, clinician));
		List<PrescriptionItem> items = toItems(prescription, request.items());
		itemRepository.saveAll(items);
		recordHistory(prescription, clinician, PrescriptionHistoryEventType.CREATED, items);
		return toResponse(prescription, items);
	}

	@Transactional(readOnly = true)
	public List<PrescriptionResponse> getForEncounter(Long encounterId, HospitalUserPrincipal principal) {
		Encounter encounter = encounterRepository.findById(encounterId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Encounter not found"));
		assertCanReadEncounter(encounter, principal);
		return prescriptionRepository.findByEncounterIdOrderByCreatedAtDescIdDesc(encounterId).stream()
			.filter(prescription -> principal.getRole() != UserRole.PATIENT
				|| prescription.getStatus() == PrescriptionStatus.FINALIZED)
			.map(prescription -> toResponse(prescription,
				itemRepository.findByPrescriptionIdOrderByItemOrderAsc(prescription.getId())))
			.toList();
	}

	@Transactional(readOnly = true)
	public PrescriptionResponse getById(Long prescriptionId, HospitalUserPrincipal principal) {
		Prescription prescription = findPrescription(prescriptionId);
		assertCanReadPrescription(prescription, principal);
		return toResponse(prescription,
			itemRepository.findByPrescriptionIdOrderByItemOrderAsc(prescriptionId));
	}

	@Transactional(readOnly = true)
	public List<PrescriptionHistoryResponse> getHistory(Long prescriptionId, HospitalUserPrincipal principal) {
		Prescription prescription = findPrescription(prescriptionId);
		assertCanReadPrescription(prescription, principal);
		if (principal.getRole() == UserRole.PATIENT) {
			throw new AccessDeniedException("Prescription history is restricted to clinicians");
		}
		return historyRepository.findByPrescriptionIdOrderByOccurredAtAscIdAsc(prescriptionId).stream()
			.map(history -> toHistoryResponse(history,
				historyItemRepository.findByHistoryIdOrderByItemOrderAsc(history.getId())))
			.toList();
	}

	@Transactional
	public PrescriptionResponse update(Long prescriptionId, PrescriptionUpdateRequest request,
			HospitalUserPrincipal principal) {
		Prescription prescription = prescriptionRepository.findByIdForUpdate(prescriptionId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Prescription not found"));
		assertAssignedDoctor(prescription.getEncounter(), principal);
		if (prescription.getStatus() == PrescriptionStatus.FINALIZED) {
			throw new ApiException(HttpStatus.CONFLICT, "Finalized prescriptions cannot be changed");
		}

		itemRepository.deleteByPrescriptionId(prescriptionId);
		List<PrescriptionItem> items = toItems(prescription, request.items());
		itemRepository.saveAll(items);
		prescription.markUpdated();
		UserAccount clinician = userRepository.getReferenceById(principal.getUserId());
		recordHistory(prescription, clinician, PrescriptionHistoryEventType.UPDATED, items);
		return toResponse(prescription, items);
	}

	@Transactional
	public PrescriptionResponse finalizePrescription(Long prescriptionId, HospitalUserPrincipal principal) {
		Prescription prescription = prescriptionRepository.findByIdForUpdate(prescriptionId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Prescription not found"));
		assertAssignedDoctor(prescription.getEncounter(), principal);
		if (prescription.getStatus() == PrescriptionStatus.FINALIZED) {
			throw new ApiException(HttpStatus.CONFLICT, "Prescription is already finalized");
		}
		List<PrescriptionItem> items = itemRepository.findByPrescriptionIdOrderByItemOrderAsc(prescriptionId);
		if (items.isEmpty()) {
			throw new ApiException(HttpStatus.CONFLICT, "A prescription needs at least one item before finalization");
		}

		prescription.finalizePrescription();
		UserAccount clinician = userRepository.getReferenceById(principal.getUserId());
		recordHistory(prescription, clinician, PrescriptionHistoryEventType.FINALIZED, items);
		return toResponse(prescription, items);
	}

	private void recordHistory(Prescription prescription, UserAccount actor,
			PrescriptionHistoryEventType eventType, List<PrescriptionItem> items) {
		PrescriptionHistory history = historyRepository.save(new PrescriptionHistory(prescription, actor,
			eventType, prescription.getStatus()));
		historyItemRepository.saveAll(items.stream()
			.map(item -> new PrescriptionHistoryItem(history, item)).toList());
	}

	private Prescription findPrescription(Long id) {
		return prescriptionRepository.findById(id)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Prescription not found"));
	}

	private static List<PrescriptionItem> toItems(Prescription prescription,
			List<PrescriptionItemRequest> requests) {
		return java.util.stream.IntStream.range(0, requests.size())
			.mapToObj(index -> {
				PrescriptionItemRequest request = requests.get(index);
				return new PrescriptionItem(prescription, index, request.medicineName().trim(),
					request.dosage().trim(), request.route().trim(), request.frequency().trim(),
					request.duration().trim(), request.quantity(), blankToNull(request.instructions()));
			}).toList();
	}

	private static void assertAssignedDoctor(Encounter encounter, HospitalUserPrincipal principal) {
		if (principal.getRole() != UserRole.DOCTOR
				|| !encounter.getDoctor().getUser().getId().equals(principal.getUserId())
				|| encounter.getPatient().getAssignedDoctor() == null
				|| !encounter.getPatient().getAssignedDoctor().getId().equals(encounter.getDoctor().getId())) {
			throw new AccessDeniedException("Only the assigned doctor may manage this prescription");
		}
	}

	private static void assertCanReadEncounter(Encounter encounter, HospitalUserPrincipal principal) {
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
		throw new AccessDeniedException("You cannot access this prescription");
	}

	private static void assertCanReadPrescription(Prescription prescription, HospitalUserPrincipal principal) {
		assertCanReadEncounter(prescription.getEncounter(), principal);
		if (principal.getRole() == UserRole.PATIENT
				&& prescription.getStatus() != PrescriptionStatus.FINALIZED) {
			throw new AccessDeniedException("Draft prescriptions are not available to patients");
		}
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private static PrescriptionResponse toResponse(Prescription prescription, List<PrescriptionItem> items) {
		return new PrescriptionResponse(prescription.getId(), prescription.getEncounter().getId(),
			prescription.getStatus(), prescription.getPrescribedBy().getId(), prescription.getCreatedAt(),
			prescription.getUpdatedAt(), prescription.getFinalizedAt(),
			items.stream().map(PrescriptionService::toItemResponse).toList());
	}

	private static PrescriptionItemResponse toItemResponse(PrescriptionItem item) {
		return new PrescriptionItemResponse(item.getId(), item.getItemOrder(), item.getMedicineName(),
			item.getDosage(), item.getRoute(), item.getFrequency(), item.getDuration(),
			item.getQuantity(), item.getInstructions());
	}

	private static PrescriptionHistoryResponse toHistoryResponse(PrescriptionHistory history,
			List<PrescriptionHistoryItem> items) {
		return new PrescriptionHistoryResponse(history.getId(), history.getChangedBy().getId(),
			history.getEventType(), history.getPrescriptionStatus(), history.getOccurredAt(),
			items.stream().map(item -> new PrescriptionHistoryItemResponse(item.getItemOrder(),
				item.getMedicineName(), item.getDosage(), item.getRoute(), item.getFrequency(),
				item.getDuration(), item.getQuantity(), item.getInstructions())).toList());
	}
}