package com.smart_hospital_ai.demo.repository;

import java.util.List;
import java.util.Optional;
import com.smart_hospital_ai.demo.entity.MedicalReport;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MedicalReportRepository extends JpaRepository<MedicalReport, Long> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select report from MedicalReport report where report.id = :id")
	Optional<MedicalReport> findByIdForUpdate(@Param("id") Long id);

	List<MedicalReport> findByEncounterIdOrderByCreatedAtDescIdDesc(Long encounterId);
}