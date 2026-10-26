package com.smart_hospital_ai.demo.repository;

import java.util.List;
import java.util.Optional;
import com.smart_hospital_ai.demo.entity.LabResult;
import com.smart_hospital_ai.demo.entity.LabResultStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LabResultRepository extends JpaRepository<LabResult, Long> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from LabResult r where r.id = :id")
	Optional<LabResult> findByIdForUpdate(@Param("id") Long id);

	Optional<LabResult> findFirstByLabOrderIdOrderByResultVersionDesc(Long labOrderId);

	List<LabResult> findByLabOrderIdOrderByResultVersionDesc(Long labOrderId);

	@Query("""
		select result from LabResult result
		where result.labOrder.encounter.id = :encounterId
		and result.status = :status
		and not exists (
			select later.id from LabResult later
			where later.labOrder.id = result.labOrder.id
			and later.resultVersion > result.resultVersion
		)
		order by result.createdAt asc, result.id asc
		""")
	List<LabResult> findCurrentByEncounterAndStatus(@Param("encounterId") Long encounterId,
			@Param("status") LabResultStatus status);
}