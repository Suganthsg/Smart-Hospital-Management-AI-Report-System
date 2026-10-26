package com.smart_hospital_ai.demo.repository;

import java.util.List;
import com.smart_hospital_ai.demo.entity.DoctorAvailability;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DoctorAvailabilityRepository extends JpaRepository<DoctorAvailability, Long> {

	List<DoctorAvailability> findByDoctorIdOrderByDayOfWeekAscStartTimeAsc(Long doctorId);

	List<DoctorAvailability> findByDoctorIdAndDayOfWeek(Long doctorId, short dayOfWeek);

	boolean existsByDoctorId(Long doctorId);

	void deleteByIdAndDoctorId(Long id, Long doctorId);
}