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
@Table(name = "lab_orders")
public class LabOrder {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "encounter_id", nullable = false, updatable = false)
	private Encounter encounter;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "ordered_by_user_id", nullable = false, updatable = false)
	private UserAccount orderedBy;

	@Column(name = "test_name", nullable = false, length = 200)
	private String testName;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private LabOrderStatus status;

	@Version
	@Column(nullable = false)
	private long version;

	@Column(name = "cancelled_at")
	private OffsetDateTime cancelledAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	protected LabOrder() {
	}

	public LabOrder(Encounter encounter, UserAccount orderedBy, String testName) {
		this.encounter = encounter;
		this.orderedBy = orderedBy;
		this.testName = testName;
		this.status = LabOrderStatus.ORDERED;
		this.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
		this.updatedAt = this.createdAt;
	}

	public Long getId() {
		return id;
	}

	public Encounter getEncounter() {
		return encounter;
	}

	public UserAccount getOrderedBy() {
		return orderedBy;
	}

	public String getTestName() {
		return testName;
	}

	public LabOrderStatus getStatus() {
		return status;
	}

	public OffsetDateTime getCancelledAt() {
		return cancelledAt;
	}

	public OffsetDateTime getCreatedAt() {
		return createdAt;
	}

	public OffsetDateTime getUpdatedAt() {
		return updatedAt;
	}

	public void resultEntered() {
		this.status = LabOrderStatus.RESULTED;
		this.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
	}

	public void resultVerified() {
		this.status = LabOrderStatus.VERIFIED;
		this.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
	}

	public void cancel() {
		this.status = LabOrderStatus.CANCELLED;
		this.cancelledAt = OffsetDateTime.now(ZoneOffset.UTC);
		this.updatedAt = this.cancelledAt;
	}
}