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
@Table(name = "medical_reports")
public class MedicalReport {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "encounter_id", nullable = false, updatable = false)
	private Encounter encounter;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "author_user_id", nullable = false, updatable = false)
	private UserAccount author;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "reviewer_user_id")
	private UserAccount reviewer;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private MedicalReportStatus status;

	@Enumerated(EnumType.STRING)
	@Column(name = "generation_mode", nullable = false, length = 20, updatable = false)
	private ReportGenerationMode generationMode;

	@Column(nullable = false, columnDefinition = "text")
	private String content;

	@Column(name = "submitted_at")
	private OffsetDateTime submittedAt;

	@Column(name = "reviewed_at")
	private OffsetDateTime reviewedAt;

	@Column(name = "reviewer_notes", columnDefinition = "text")
	private String reviewerNotes;

	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	protected MedicalReport() {
	}

	public MedicalReport(Encounter encounter, UserAccount author, String content,
			ReportGenerationMode generationMode) {
		this.encounter = encounter;
		this.author = author;
		this.content = content;
		this.generationMode = generationMode;
		this.status = MedicalReportStatus.DRAFT;
		this.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
		this.updatedAt = this.createdAt;
	}

	public Long getId() {
		return id;
	}

	public Encounter getEncounter() {
		return encounter;
	}

	public UserAccount getAuthor() {
		return author;
	}

	public UserAccount getReviewer() {
		return reviewer;
	}

	public MedicalReportStatus getStatus() {
		return status;
	}

	public ReportGenerationMode getGenerationMode() {
		return generationMode;
	}

	public String getContent() {
		return content;
	}

	public OffsetDateTime getSubmittedAt() {
		return submittedAt;
	}

	public OffsetDateTime getReviewedAt() {
		return reviewedAt;
	}

	public String getReviewerNotes() {
		return reviewerNotes;
	}

	public OffsetDateTime getCreatedAt() {
		return createdAt;
	}

	public OffsetDateTime getUpdatedAt() {
		return updatedAt;
	}

	public void edit(String content) {
		this.content = content;
		this.status = MedicalReportStatus.DRAFT;
		this.reviewer = null;
		this.reviewedAt = null;
		this.reviewerNotes = null;
		this.submittedAt = null;
		this.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
	}

	public void submit() {
		this.status = MedicalReportStatus.IN_REVIEW;
		this.submittedAt = OffsetDateTime.now(ZoneOffset.UTC);
		this.updatedAt = this.submittedAt;
	}

	public void approve(UserAccount reviewer, String notes) {
		this.status = MedicalReportStatus.APPROVED;
		this.reviewer = reviewer;
		this.reviewedAt = OffsetDateTime.now(ZoneOffset.UTC);
		this.reviewerNotes = notes;
		this.updatedAt = this.reviewedAt;
	}

	public void reject(UserAccount reviewer, String notes) {
		this.status = MedicalReportStatus.REJECTED;
		this.reviewer = reviewer;
		this.reviewedAt = OffsetDateTime.now(ZoneOffset.UTC);
		this.reviewerNotes = notes;
		this.updatedAt = this.reviewedAt;
	}
}