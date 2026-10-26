package com.smart_hospital_ai.demo.repository;

import java.util.List;
import com.smart_hospital_ai.demo.entity.PrescriptionHistoryItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrescriptionHistoryItemRepository extends JpaRepository<PrescriptionHistoryItem, Long> {

	List<PrescriptionHistoryItem> findByHistoryIdOrderByItemOrderAsc(Long historyId);
}