package com.smart_hospital_ai.demo.entity;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "prescription_items")
public class PrescriptionItem {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "prescription_id", nullable = false, updatable = false)
	private Prescription prescription;

	@Column(name = "item_order", nullable = false)
	private int itemOrder;

	@Column(name = "medicine_name", nullable = false, length = 200)
	private String medicineName;

	@Column(nullable = false, length = 200)
	private String dosage;

	@Column(nullable = false, length = 100)
	private String route;

	@Column(nullable = false, length = 200)
	private String frequency;

	@Column(nullable = false, length = 100)
	private String duration;

	@Column(nullable = false)
	private int quantity;

	@Column(columnDefinition = "text")
	private String instructions;

	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	protected PrescriptionItem() {
	}

	public PrescriptionItem(Prescription prescription, int itemOrder, String medicineName, String dosage,
			String route, String frequency, String duration, int quantity, String instructions) {
		this.prescription = prescription;
		this.itemOrder = itemOrder;
		this.medicineName = medicineName;
		this.dosage = dosage;
		this.route = route;
		this.frequency = frequency;
		this.duration = duration;
		this.quantity = quantity;
		this.instructions = instructions;
		this.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
	}

	public Long getId() {
		return id;
	}

	public Prescription getPrescription() {
		return prescription;
	}

	public int getItemOrder() {
		return itemOrder;
	}

	public String getMedicineName() {
		return medicineName;
	}

	public String getDosage() {
		return dosage;
	}

	public String getRoute() {
		return route;
	}

	public String getFrequency() {
		return frequency;
	}

	public String getDuration() {
		return duration;
	}

	public int getQuantity() {
		return quantity;
	}

	public String getInstructions() {
		return instructions;
	}
}