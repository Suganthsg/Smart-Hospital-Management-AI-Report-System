package com.smart_hospital_ai.demo.repository;

import java.util.List;
import com.smart_hospital_ai.demo.entity.MedicalReportHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MedicalReportHistoryRepository extends JpaRepository<MedicalReportHistory, Long> {

	List<MedicalReportHistory> findByReportIdOrderByOccurredAtAscIdAsc(Long reportId);
}