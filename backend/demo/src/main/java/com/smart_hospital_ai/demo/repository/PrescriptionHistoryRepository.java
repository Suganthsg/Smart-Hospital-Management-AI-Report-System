package com.smart_hospital_ai.demo.repository;

import java.util.List;
import com.smart_hospital_ai.demo.entity.PrescriptionHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrescriptionHistoryRepository extends JpaRepository<PrescriptionHistory, Long> {

	List<PrescriptionHistory> findByPrescriptionIdOrderByOccurredAtAscIdAsc(Long prescriptionId);
}