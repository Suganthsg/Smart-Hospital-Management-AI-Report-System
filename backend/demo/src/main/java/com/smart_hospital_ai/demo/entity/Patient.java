package com.smart_hospital_ai.demo.entity;

import java.time.LocalDate;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "patients")
public class Patient {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false, unique = true)
	private UserAccount user;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "assigned_doctor_id")
	private Doctor assignedDoctor;

	@Column(name = "medical_record_number", nullable = false, length = 64, unique = true)
	private String medicalRecordNumber;

	@Column(name = "first_name", nullable = false, length = 100)
	private String firstName;

	@Column(name = "last_name", nullable = false, length = 100)
	private String lastName;

	@Column(name = "date_of_birth")
	private LocalDate dateOfBirth;

	@Column(length = 32)
	private String phone;

	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	protected Patient() {
	}

	public Patient(UserAccount user, String medicalRecordNumber, String firstName, String lastName,
			LocalDate dateOfBirth, String phone) {
		this.user = user;
		this.medicalRecordNumber = medicalRecordNumber;
		this.firstName = firstName;
		this.lastName = lastName;
		this.dateOfBirth = dateOfBirth;
		this.phone = phone;
		this.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
		this.updatedAt = this.createdAt;
	}

	public Long getId() {
		return id;
	}

	public UserAccount getUser() {
		return user;
	}

	public Doctor getAssignedDoctor() {
		return assignedDoctor;
	}

	public String getMedicalRecordNumber() {
		return medicalRecordNumber;
	}

	public String getFirstName() {
		return firstName;
	}

	public String getLastName() {
		return lastName;
	}

	public LocalDate getDateOfBirth() {
		return dateOfBirth;
	}

	public String getPhone() {
		return phone;
	}

	public void updateDetails(String firstName, String lastName, LocalDate dateOfBirth, String phone) {
		this.firstName = firstName;
		this.lastName = lastName;
		this.dateOfBirth = dateOfBirth;
		this.phone = phone;
		this.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
	}

	public void assignDoctor(Doctor doctor) {
		this.assignedDoctor = doctor;
		this.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
	}
}