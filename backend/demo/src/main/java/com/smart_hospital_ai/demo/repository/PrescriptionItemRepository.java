package com.smart_hospital_ai.demo.repository;

import java.util.List;
import com.smart_hospital_ai.demo.entity.PrescriptionItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PrescriptionItemRepository extends JpaRepository<PrescriptionItem, Long> {

	List<PrescriptionItem> findByPrescriptionIdOrderByItemOrderAsc(Long prescriptionId);

	@Modifying
	@Query("delete from PrescriptionItem item where item.prescription.id = :prescriptionId")
	void deleteByPrescriptionId(@Param("prescriptionId") Long prescriptionId);
}