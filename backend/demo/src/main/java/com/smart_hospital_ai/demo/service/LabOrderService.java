package com.smart_hospital_ai.demo.service;

import java.util.ArrayList;
import java.util.List;
import com.smart_hospital_ai.demo.dto.LabOrderCreateRequest;
import com.smart_hospital_ai.demo.dto.LabOrderResponse;
import com.smart_hospital_ai.demo.dto.LabResultCreateRequest;
import com.smart_hospital_ai.demo.dto.LabResultResponse;
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
public class LabOrderService {

	private static final int MAX_PAGE_SIZE = 100;
	private final LabOrderRepository orderRepository;
	private final LabResultRepository resultRepository;
	private final EncounterRepository encounterRepository;
	private final DoctorRepository doctorRepository;
	private final PatientRepository patientRepository;
	private final UserRepository userRepository;

	public LabOrderService(LabOrderRepository orderRepository, LabResultRepository resultRepository,
			EncounterRepository encounterRepository, DoctorRepository doctorRepository,
			PatientRepository patientRepository, UserRepository userRepository) {
		this.orderRepository = orderRepository;
		this.resultRepository = resultRepository;
		this.encounterRepository = encounterRepository;
		this.doctorRepository = doctorRepository;
		this.patientRepository = patientRepository;
		this.userRepository = userRepository;
	}

	@Transactional
	public LabOrderResponse createOrder(Long encounterId, LabOrderCreateRequest request,
			HospitalUserPrincipal principal) {
		Encounter encounter = encounterRepository.findByIdForUpdate(encounterId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Encounter not found"));
		assertAssignedDoctor(encounter, principal);
		UserAccount clinician = userRepository.getReferenceById(principal.getUserId());
		LabOrder order = orderRepository.save(new LabOrder(encounter, clinician, request.testName().trim()));
		return toOrderResponse(order);
	}

	@Transactional(readOnly = true)
	public Page<LabOrderResponse> worklist(LabOrderStatus requestedStatus, int page, int size,
			HospitalUserPrincipal principal) {
		assertLabTechnician(principal);
		validatePage(page, size);
		if (requestedStatus != null && requestedStatus != LabOrderStatus.ORDERED
				&& requestedStatus != LabOrderStatus.RESULTED) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "The laboratory worklist only supports ORDERED or RESULTED orders");
		}

		Specification<LabOrder> specification = (root, query, criteriaBuilder) -> {
			List<Predicate> predicates = new ArrayList<>();
			if (requestedStatus == null) {
				predicates.add(root.get("status").in(LabOrderStatus.ORDERED, LabOrderStatus.RESULTED));
			} else {
				predicates.add(criteriaBuilder.equal(root.get("status"), requestedStatus));
			}
			return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
		};
		PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").ascending().and(Sort.by("id").ascending()));
		return orderRepository.findAll(specification, pageable).map(LabOrderService::toOrderResponse);
	}

	@Transactional(readOnly = true)
	public List<LabOrderResponse> getForEncounter(Long encounterId, HospitalUserPrincipal principal) {
		Encounter encounter = encounterRepository.findById(encounterId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Encounter not found"));
		assertCanReadEncounter(encounter, principal);
		return orderRepository.findByEncounterIdOrderByCreatedAtDescIdDesc(encounterId)
			.stream().map(LabOrderService::toOrderResponse).toList();
	}

	@Transactional
	public LabResultResponse enterResult(Long orderId, LabResultCreateRequest request,
			HospitalUserPrincipal principal) {
		assertLabTechnician(principal);
		LabOrder order = findOrderForUpdate(orderId);
		if (order.getStatus() == LabOrderStatus.CANCELLED) {
			throw new ApiException(HttpStatus.CONFLICT, "Results cannot be entered for a cancelled order");
		}
		LabResult previous = resultRepository.findFirstByLabOrderIdOrderByResultVersionDesc(orderId).orElse(null);
		int nextVersion = previous == null ? 1 : previous.getResultVersion() + 1;
		UserAccount technician = userRepository.getReferenceById(principal.getUserId());
		LabResult result = resultRepository.save(new LabResult(order, nextVersion,
			request.resultValue().trim(), blankToNull(request.units()), blankToNull(request.referenceRange()),
			blankToNull(request.notes()), technician, previous));
		order.resultEntered();
		return toResultResponse(result);
	}

	@Transactional(readOnly = true)
	public List<LabResultResponse> getResults(Long orderId, HospitalUserPrincipal principal) {
		LabOrder order = findOrder(orderId);
		boolean patient = isPatientOwner(order.getEncounter(), principal);
		assertCanReadEncounterOrLab(order, principal);
		List<LabResult> results = resultRepository.findByLabOrderIdOrderByResultVersionDesc(orderId);
		if (patient) {
			results = results.stream().findFirst()
				.filter(result -> result.getStatus() == LabResultStatus.VERIFIED)
				.map(List::of).orElseGet(List::of);
		}
		return results.stream().map(LabOrderService::toResultResponse).toList();
	}

	@Transactional
	public LabResultResponse verifyResult(Long orderId, Long resultId, HospitalUserPrincipal principal) {
		LabOrder order = findOrderForUpdate(orderId);
		assertAssignedDoctor(order.getEncounter(), principal);
		if (order.getStatus() == LabOrderStatus.CANCELLED) {
			throw new ApiException(HttpStatus.CONFLICT, "Results for a cancelled order cannot be verified");
		}
		LabResult latest = resultRepository.findFirstByLabOrderIdOrderByResultVersionDesc(orderId)
			.orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "The order has no result to verify"));
		if (!latest.getId().equals(resultId)) {
			throw new ApiException(HttpStatus.CONFLICT, "Only the latest result version can be verified");
		}
		LabResult result = resultRepository.findByIdForUpdate(resultId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Lab result not found"));
		if (result.getStatus() == LabResultStatus.VERIFIED) {
			throw new ApiException(HttpStatus.CONFLICT, "Lab result is already verified");
		}
		UserAccount doctor = userRepository.getReferenceById(principal.getUserId());
		result.verify(doctor);
		order.resultVerified();
		return toResultResponse(result);
	}

	@Transactional(readOnly = true)
	public List<LabResultResponse> getVerifiedResultsForEncounter(Long encounterId,
			HospitalUserPrincipal principal) {
		Encounter encounter = encounterRepository.findById(encounterId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Encounter not found"));
		if (principal.getRole() != UserRole.ADMIN && !isPatientOwner(encounter, principal)) {
			assertAssignedDoctor(encounter, principal);
		}
		return resultRepository.findCurrentByEncounterAndStatus(
			encounterId, LabResultStatus.VERIFIED).stream().map(LabOrderService::toResultResponse).toList();
	}

	private LabOrder findOrder(Long orderId) {
		return orderRepository.findById(orderId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Lab order not found"));
	}

	private LabOrder findOrderForUpdate(Long orderId) {
		return orderRepository.findByIdForUpdate(orderId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Lab order not found"));
	}

	private static void validatePage(int page, int size) {
		if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Page must be non-negative and size must be between 1 and 100");
		}
	}

	private static void assertLabTechnician(HospitalUserPrincipal principal) {
		if (principal.getRole() != UserRole.LAB_TECHNICIAN) {
			throw new AccessDeniedException("Only laboratory technicians may use the lab worklist or enter results");
		}
	}

	private static void assertAssignedDoctor(Encounter encounter, HospitalUserPrincipal principal) {
		if (principal.getRole() != UserRole.DOCTOR
				|| !encounter.getDoctor().getUser().getId().equals(principal.getUserId())
				|| encounter.getPatient().getAssignedDoctor() == null
				|| !encounter.getPatient().getAssignedDoctor().getId().equals(encounter.getDoctor().getId())) {
			throw new AccessDeniedException("Only the assigned doctor may manage this clinical record");
		}
	}

	private static boolean isPatientOwner(Encounter encounter, HospitalUserPrincipal principal) {
		return principal.getRole() == UserRole.PATIENT
			&& encounter.getPatient().getUser().getId().equals(principal.getUserId());
	}

	private static void assertCanReadEncounter(Encounter encounter, HospitalUserPrincipal principal) {
		if (principal.getRole() == UserRole.ADMIN || isPatientOwner(encounter, principal)) {
			return;
		}
		assertAssignedDoctor(encounter, principal);
	}

	private static void assertCanReadEncounterOrLab(LabOrder order, HospitalUserPrincipal principal) {
		if (principal.getRole() == UserRole.LAB_TECHNICIAN) {
			return;
		}
		assertCanReadEncounter(order.getEncounter(), principal);
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private static LabOrderResponse toOrderResponse(LabOrder order) {
		Encounter encounter = order.getEncounter();
		return new LabOrderResponse(order.getId(), encounter.getId(), encounter.getPatient().getId(),
			encounter.getPatient().getMedicalRecordNumber(), encounter.getDoctor().getId(),
			encounter.getDoctor().getFirstName() + " " + encounter.getDoctor().getLastName(),
			encounter.getEncounterDate(), order.getTestName(), order.getStatus(),
			order.getOrderedBy().getId(), order.getCreatedAt(), order.getUpdatedAt());
	}

	private static LabResultResponse toResultResponse(LabResult result) {
		return new LabResultResponse(result.getId(), result.getLabOrder().getId(), result.getResultVersion(),
			result.getResultValue(), result.getUnits(), result.getReferenceRange(), result.getNotes(),
			result.getStatus(), result.getEnteredBy().getId(),
			result.getVerifiedBy() == null ? null : result.getVerifiedBy().getId(), result.getVerifiedAt(),
			result.getPreviousResult() == null ? null : result.getPreviousResult().getId(), result.getCreatedAt());
	}
}