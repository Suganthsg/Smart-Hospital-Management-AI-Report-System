package com.smart_hospital_ai.demo.dto;

public record PrescriptionHistoryItemResponse(int itemOrder, String medicineName, String dosage,
		String route, String frequency, String duration, int quantity, String instructions) {
}