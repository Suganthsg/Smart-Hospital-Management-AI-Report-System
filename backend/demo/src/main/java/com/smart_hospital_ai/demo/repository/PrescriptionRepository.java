package com.smart_hospital_ai.demo.repository;

import java.util.List;
import java.util.Optional;
import com.smart_hospital_ai.demo.entity.Prescription;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PrescriptionRepository extends JpaRepository<Prescription, Long> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from Prescription p where p.id = :id")
	Optional<Prescription> findByIdForUpdate(@Param("id") Long id);

	List<Prescription> findByEncounterIdOrderByCreatedAtDescIdDesc(Long encounterId);
}