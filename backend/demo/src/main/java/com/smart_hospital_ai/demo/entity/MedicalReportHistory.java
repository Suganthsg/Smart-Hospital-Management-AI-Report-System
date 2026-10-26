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
@Table(name = "medical_report_history")
public class MedicalReportHistory {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "report_id", nullable = false, updatable = false)
	private MedicalReport report;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "actor_user_id", nullable = false, updatable = false)
	private UserAccount actor;

	@Enumerated(EnumType.STRING)
	@Column(name = "event_type", nullable = false, length = 20, updatable = false)
	private MedicalReportHistoryEventType eventType;

	@Enumerated(EnumType.STRING)
	@Column(name = "report_status", nullable = false, length = 20, updatable = false)
	private MedicalReportStatus reportStatus;

	@Column(name = "content_snapshot", nullable = false, columnDefinition = "text", updatable = false)
	private String contentSnapshot;

	@Column(name = "event_note", columnDefinition = "text", updatable = false)
	private String eventNote;

	@Column(name = "occurred_at", nullable = false, updatable = false)
	private OffsetDateTime occurredAt;

	protected MedicalReportHistory() {
	}

	public MedicalReportHistory(MedicalReport report, UserAccount actor,
			MedicalReportHistoryEventType eventType, String eventNote) {
		this.report = report;
		this.actor = actor;
		this.eventType = eventType;
		this.reportStatus = report.getStatus();
		this.contentSnapshot = report.getContent();
		this.eventNote = eventNote;
		this.occurredAt = OffsetDateTime.now(ZoneOffset.UTC);
	}

	public Long getId() {
		return id;
	}

	public MedicalReport getReport() {
		return report;
	}

	public UserAccount getActor() {
		return actor;
	}

	public MedicalReportHistoryEventType getEventType() {
		return eventType;
	}

	public MedicalReportStatus getReportStatus() {
		return reportStatus;
	}

	public String getContentSnapshot() {
		return contentSnapshot;
	}

	public String getEventNote() {
		return eventNote;
	}

	public OffsetDateTime getOccurredAt() {
		return occurredAt;
	}
}