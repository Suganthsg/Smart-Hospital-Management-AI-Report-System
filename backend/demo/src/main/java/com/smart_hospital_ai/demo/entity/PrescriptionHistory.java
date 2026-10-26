package com.smart_hospital_ai.demo.entity;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "prescription_history")
public class PrescriptionHistory {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "prescription_id", nullable = false, updatable = false)
	private Prescription prescription;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "changed_by_user_id", nullable = false, updatable = false)
	private UserAccount changedBy;

	@Enumerated(EnumType.STRING)
	@Column(name = "event_type", nullable = false, length = 20, updatable = false)
	private PrescriptionHistoryEventType eventType;

	@Enumerated(EnumType.STRING)
	@Column(name = "prescription_status", nullable = false, length = 20, updatable = false)
	private PrescriptionStatus prescriptionStatus;

	@Column(name = "occurred_at", nullable = false, updatable = false)
	private OffsetDateTime occurredAt;

	protected PrescriptionHistory() {
	}

	public PrescriptionHistory(Prescription prescription, UserAccount changedBy,
			PrescriptionHistoryEventType eventType, PrescriptionStatus prescriptionStatus) {
		this.prescription = prescription;
		this.changedBy = changedBy;
		this.eventType = eventType;
		this.prescriptionStatus = prescriptionStatus;
		this.occurredAt = OffsetDateTime.now(ZoneOffset.UTC);
	}

	public Long getId() {
		return id;
	}

	public Prescription getPrescription() {
		return prescription;
	}

	public UserAccount getChangedBy() {
		return changedBy;
	}

	public PrescriptionHistoryEventType getEventType() {
		return eventType;
	}

	public PrescriptionStatus getPrescriptionStatus() {
		return prescriptionStatus;
	}

	public OffsetDateTime getOccurredAt() {
		return occurredAt;
	}
}