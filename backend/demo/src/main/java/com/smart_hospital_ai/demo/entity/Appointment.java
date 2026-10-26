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
@Table(name = "appointments")
public class Appointment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "patient_id", nullable = false)
	private Patient patient;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "doctor_id", nullable = false)
	private Doctor doctor;

	@Column(name = "start_at", nullable = false)
	private OffsetDateTime startAt;

	@Column(name = "end_at", nullable = false)
	private OffsetDateTime endAt;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private AppointmentStatus status;

	@Column(length = 500)
	private String reason;

	@Column(name = "cancelled_at")
	private OffsetDateTime cancelledAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	protected Appointment() {
	}

	public Appointment(Patient patient, Doctor doctor, OffsetDateTime startAt,
			OffsetDateTime endAt, String reason) {
		this.patient = patient;
		this.doctor = doctor;
		this.startAt = startAt;
		this.endAt = endAt;
		this.reason = reason;
		this.status = AppointmentStatus.SCHEDULED;
		this.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
		this.updatedAt = this.createdAt;
	}

	public Long getId() {
		return id;
	}

	public Patient getPatient() {
		return patient;
	}

	public Doctor getDoctor() {
		return doctor;
	}

	public OffsetDateTime getStartAt() {
		return startAt;
	}

	public OffsetDateTime getEndAt() {
		return endAt;
	}

	public AppointmentStatus getStatus() {
		return status;
	}

	public String getReason() {
		return reason;
	}

	public OffsetDateTime getCancelledAt() {
		return cancelledAt;
	}

	public void reschedule(OffsetDateTime startAt, OffsetDateTime endAt) {
		this.startAt = startAt;
		this.endAt = endAt;
		this.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
	}

	public void cancel() {
		this.status = AppointmentStatus.CANCELLED;
		this.cancelledAt = OffsetDateTime.now(ZoneOffset.UTC);
		this.updatedAt = this.cancelledAt;
	}
}