package com.smart_hospital_ai.demo.repository;

import java.time.LocalDate;
import java.util.Optional;
import com.smart_hospital_ai.demo.entity.Encounter;
import com.smart_hospital_ai.demo.entity.EncounterStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EncounterRepository extends JpaRepository<Encounter, Long>, JpaSpecificationExecutor<Encounter> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select e from Encounter e where e.id = :id")
	Optional<Encounter> findByIdForUpdate(@Param("id") Long id);

	boolean existsByPatientId(Long patientId);

	boolean existsByDoctorId(Long doctorId);

}