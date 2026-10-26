package com.smart_hospital_ai.demo.entity;

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
@Table(name = "prescription_history_items")
public class PrescriptionHistoryItem {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "history_id", nullable = false, updatable = false)
	private PrescriptionHistory history;

	@Column(name = "item_order", nullable = false, updatable = false)
	private int itemOrder;

	@Column(name = "medicine_name", nullable = false, length = 200, updatable = false)
	private String medicineName;

	@Column(nullable = false, length = 200, updatable = false)
	private String dosage;

	@Column(nullable = false, length = 100, updatable = false)
	private String route;

	@Column(nullable = false, length = 200, updatable = false)
	private String frequency;

	@Column(nullable = false, length = 100, updatable = false)
	private String duration;

	@Column(nullable = false, updatable = false)
	private int quantity;

	@Column(columnDefinition = "text", updatable = false)
	private String instructions;

	protected PrescriptionHistoryItem() {
	}

	public PrescriptionHistoryItem(PrescriptionHistory history, PrescriptionItem item) {
		this.history = history;
		this.itemOrder = item.getItemOrder();
		this.medicineName = item.getMedicineName();
		this.dosage = item.getDosage();
		this.route = item.getRoute();
		this.frequency = item.getFrequency();
		this.duration = item.getDuration();
		this.quantity = item.getQuantity();
		this.instructions = item.getInstructions();
	}

	public Long getId() {
		return id;
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