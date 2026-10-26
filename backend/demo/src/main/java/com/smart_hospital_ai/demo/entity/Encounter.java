package com.smart_hospital_ai.demo.entity;

import java.time.LocalDate;
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
@Table(name = "clinical_encounters")
public class Encounter {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "patient_id", nullable = false)
	private Patient patient;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "doctor_id", nullable = false)
	private Doctor doctor;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "appointment_id")
	private Appointment appointment;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "created_by_user_id", nullable = false, updatable = false)
	private UserAccount createdBy;

	@Column(name = "encounter_date", nullable = false)
	private LocalDate encounterDate;

	@Column(name = "clinical_notes", nullable = false, columnDefinition = "text")
	private String clinicalNotes;

	@Column(nullable = false, length = 2000)
	private String diagnosis;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private EncounterStatus status;

	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	protected Encounter() {
	}

	public Encounter(Patient patient, Doctor doctor, Appointment appointment, UserAccount createdBy,
			LocalDate encounterDate, String clinicalNotes, String diagnosis) {
		this.patient = patient;
		this.doctor = doctor;
		this.appointment = appointment;
		this.createdBy = createdBy;
		this.encounterDate = encounterDate;
		this.clinicalNotes = clinicalNotes;
		this.diagnosis = diagnosis;
		this.status = EncounterStatus.DRAFT;
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

	public Appointment getAppointment() {
		return appointment;
	}

	public UserAccount getCreatedBy() {
		return createdBy;
	}

	public LocalDate getEncounterDate() {
		return encounterDate;
	}

	public String getClinicalNotes() {
		return clinicalNotes;
	}

	public String getDiagnosis() {
		return diagnosis;
	}

	public EncounterStatus getStatus() {
		return status;
	}

	public OffsetDateTime getCreatedAt() {
		return createdAt;
	}

	public OffsetDateTime getUpdatedAt() {
		return updatedAt;
	}

	public void update(LocalDate encounterDate, String clinicalNotes, String diagnosis, EncounterStatus status) {
		this.encounterDate = encounterDate;
		this.clinicalNotes = clinicalNotes;
		this.diagnosis = diagnosis;
		this.status = status;
		this.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
	}
}