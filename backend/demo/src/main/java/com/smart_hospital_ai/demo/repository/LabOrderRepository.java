package com.smart_hospital_ai.demo.repository;

import java.util.Optional;
import com.smart_hospital_ai.demo.entity.LabOrder;
import jakarta.persistence.LockModeType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LabOrderRepository extends JpaRepository<LabOrder, Long>, JpaSpecificationExecutor<LabOrder> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select o from LabOrder o where o.id = :id")
	Optional<LabOrder> findByIdForUpdate(@Param("id") Long id);

	List<LabOrder> findByEncounterIdOrderByCreatedAtDescIdDesc(Long encounterId);
}