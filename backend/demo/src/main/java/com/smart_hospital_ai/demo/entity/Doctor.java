package com.smart_hospital_ai.demo.entity;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "doctors")
public class Doctor {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false, unique = true)
	private UserAccount user;

	@Column(name = "license_number", nullable = false, length = 64, unique = true)
	private String licenseNumber;

	@Column(name = "first_name", nullable = false, length = 100)
	private String firstName;

	@Column(name = "last_name", nullable = false, length = 100)
	private String lastName;

	@Column(nullable = false, length = 150)
	private String specialization;

	@Column(length = 32)
	private String phone;

	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	protected Doctor() {
	}

	public Doctor(UserAccount user, String licenseNumber, String firstName, String lastName,
			String specialization, String phone) {
		this.user = user;
		this.licenseNumber = licenseNumber;
		this.firstName = firstName;
		this.lastName = lastName;
		this.specialization = specialization;
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

	public String getLicenseNumber() {
		return licenseNumber;
	}

	public String getFirstName() {
		return firstName;
	}

	public String getLastName() {
		return lastName;
	}

	public String getSpecialization() {
		return specialization;
	}

	public String getPhone() {
		return phone;
	}

	public void updateDetails(String licenseNumber, String firstName, String lastName,
			String specialization, String phone) {
		this.licenseNumber = licenseNumber;
		this.firstName = firstName;
		this.lastName = lastName;
		this.specialization = specialization;
		this.phone = phone;
		this.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
	}
}