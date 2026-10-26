package com.smart_hospital_ai.demo.service;

import java.time.LocalTime;
import java.util.List;
import com.smart_hospital_ai.demo.dto.DoctorAvailabilityRequest;
import com.smart_hospital_ai.demo.dto.DoctorAvailabilityResponse;
import com.smart_hospital_ai.demo.entity.Doctor;
import com.smart_hospital_ai.demo.entity.DoctorAvailability;
import com.smart_hospital_ai.demo.entity.UserRole;
import com.smart_hospital_ai.demo.exception.ApiException;
import com.smart_hospital_ai.demo.repository.DoctorAvailabilityRepository;
import com.smart_hospital_ai.demo.security.HospitalUserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DoctorAvailabilityService {

	private final DoctorService doctorService;
	private final DoctorAvailabilityRepository availabilityRepository;

	public DoctorAvailabilityService(DoctorService doctorService,
			DoctorAvailabilityRepository availabilityRepository) {
		this.doctorService = doctorService;
		this.availabilityRepository = availabilityRepository;
	}

	@Transactional(readOnly = true)
	public List<DoctorAvailabilityResponse> getForDoctor(Long doctorId) {
		doctorService.findDoctor(doctorId);
		return availabilityRepository.findByDoctorIdOrderByDayOfWeekAscStartTimeAsc(doctorId)
			.stream().map(DoctorAvailabilityService::toResponse).toList();
	}

	@Transactional
	public DoctorAvailabilityResponse add(Long doctorId, DoctorAvailabilityRequest request,
			HospitalUserPrincipal principal) {
		Doctor doctor = doctorService.findDoctor(doctorId);
		authorizeChange(doctor, principal);
		if (!request.startTime().isBefore(request.endTime())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Availability start time must be before end time");
		}
		List<DoctorAvailability> daySlots = availabilityRepository.findByDoctorIdAndDayOfWeek(
			doctorId, request.dayOfWeek());
		boolean overlaps = daySlots.stream().anyMatch(slot -> overlaps(
			request.startTime(), request.endTime(), slot.getStartTime(), slot.getEndTime()));
		if (overlaps) {
			throw new ApiException(HttpStatus.CONFLICT, "Availability overlaps an existing time slot");
		}

		DoctorAvailability availability = new DoctorAvailability(doctor, request.dayOfWeek(),
			request.startTime(), request.endTime());
		return toResponse(availabilityRepository.save(availability));
	}

	@Transactional
	public void remove(Long doctorId, Long availabilityId, HospitalUserPrincipal principal) {
		DoctorAvailability availability = availabilityRepository.findById(availabilityId)
			.filter(slot -> slot.getDoctor().getId().equals(doctorId))
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Availability slot not found"));
		authorizeChange(availability.getDoctor(), principal);
		availabilityRepository.delete(availability);
	}

	private static void authorizeChange(Doctor doctor, HospitalUserPrincipal principal) {
		boolean admin = principal.getRole() == UserRole.ADMIN;
		boolean assignedDoctor = principal.getRole() == UserRole.DOCTOR
			&& doctor.getUser().getId().equals(principal.getUserId());
		if (!admin && !assignedDoctor) {
			throw new AccessDeniedException("Only an administrator or the assigned doctor can change availability");
		}
	}

	private static boolean overlaps(LocalTime requestedStart, LocalTime requestedEnd,
			LocalTime existingStart, LocalTime existingEnd) {
		return requestedStart.isBefore(existingEnd) && requestedEnd.isAfter(existingStart);
	}

	private static DoctorAvailabilityResponse toResponse(DoctorAvailability slot) {
		return new DoctorAvailabilityResponse(slot.getId(), slot.getDoctor().getId(), slot.getDayOfWeek(),
			slot.getStartTime(), slot.getEndTime());
	}
}