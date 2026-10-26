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
import jakarta.persistence.Version;

@Entity
@Table(name = "prescriptions")
public class Prescription {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "encounter_id", nullable = false, updatable = false)
	private Encounter encounter;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "prescribed_by_user_id", nullable = false, updatable = false)
	private UserAccount prescribedBy;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private PrescriptionStatus status;

	@Column(name = "finalized_at")
	private OffsetDateTime finalizedAt;

	@Version
	@Column(nullable = false)
	private long version;

	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	protected Prescription() {
	}

	public Prescription(Encounter encounter, UserAccount prescribedBy) {
		this.encounter = encounter;
		this.prescribedBy = prescribedBy;
		this.status = PrescriptionStatus.DRAFT;
		this.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
		this.updatedAt = this.createdAt;
	}

	public Long getId() {
		return id;
	}

	public Encounter getEncounter() {
		return encounter;
	}

	public UserAccount getPrescribedBy() {
		return prescribedBy;
	}

	public PrescriptionStatus getStatus() {
		return status;
	}

	public OffsetDateTime getFinalizedAt() {
		return finalizedAt;
	}

	public OffsetDateTime getCreatedAt() {
		return createdAt;
	}

	public OffsetDateTime getUpdatedAt() {
		return updatedAt;
	}

	public void finalizePrescription() {
		this.status = PrescriptionStatus.FINALIZED;
		this.finalizedAt = OffsetDateTime.now(ZoneOffset.UTC);
		this.updatedAt = this.finalizedAt;
	}

	public void markUpdated() {
		this.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
	}
}