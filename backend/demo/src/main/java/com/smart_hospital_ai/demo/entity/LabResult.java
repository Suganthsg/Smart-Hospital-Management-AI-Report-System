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
@Table(name = "lab_results")
public class LabResult {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "lab_order_id", nullable = false, updatable = false)
	private LabOrder labOrder;

	@Column(name = "result_version", nullable = false, updatable = false)
	private int resultVersion;

	@Column(name = "result_value", nullable = false, columnDefinition = "text", updatable = false)
	private String resultValue;

	@Column(length = 100, updatable = false)
	private String units;

	@Column(name = "reference_range", length = 250, updatable = false)
	private String referenceRange;

	@Column(columnDefinition = "text", updatable = false)
	private String notes;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private LabResultStatus status;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "entered_by_user_id", nullable = false, updatable = false)
	private UserAccount enteredBy;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "verified_by_user_id")
	private UserAccount verifiedBy;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "previous_result_id", updatable = false)
	private LabResult previousResult;

	@Column(name = "verified_at")
	private OffsetDateTime verifiedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	protected LabResult() {
	}

	public LabResult(LabOrder labOrder, int resultVersion, String resultValue, String units,
			String referenceRange, String notes, UserAccount enteredBy, LabResult previousResult) {
		this.labOrder = labOrder;
		this.resultVersion = resultVersion;
		this.resultValue = resultValue;
		this.units = units;
		this.referenceRange = referenceRange;
		this.notes = notes;
		this.enteredBy = enteredBy;
		this.previousResult = previousResult;
		this.status = LabResultStatus.ENTERED;
		this.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
	}

	public Long getId() {
		return id;
	}

	public LabOrder getLabOrder() {
		return labOrder;
	}

	public int getResultVersion() {
		return resultVersion;
	}

	public String getResultValue() {
		return resultValue;
	}

	public String getUnits() {
		return units;
	}

	public String getReferenceRange() {
		return referenceRange;
	}

	public String getNotes() {
		return notes;
	}

	public LabResultStatus getStatus() {
		return status;
	}

	public UserAccount getEnteredBy() {
		return enteredBy;
	}

	public UserAccount getVerifiedBy() {
		return verifiedBy;
	}

	public LabResult getPreviousResult() {
		return previousResult;
	}

	public OffsetDateTime getVerifiedAt() {
		return verifiedAt;
	}

	public OffsetDateTime getCreatedAt() {
		return createdAt;
	}

	public void verify(UserAccount verifier) {
		this.status = LabResultStatus.VERIFIED;
		this.verifiedBy = verifier;
		this.verifiedAt = OffsetDateTime.now(ZoneOffset.UTC);
	}
}