package com.smart_hospital_ai.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalTime;
import java.util.UUID;
import com.smart_hospital_ai.demo.dto.DoctorAvailabilityRequest;
import com.smart_hospital_ai.demo.dto.DoctorAvailabilityResponse;
import com.smart_hospital_ai.demo.dto.DoctorCreateRequest;
import com.smart_hospital_ai.demo.dto.PatientRegistrationRequest;
import com.smart_hospital_ai.demo.dto.PatientResponse;
import com.smart_hospital_ai.demo.entity.UserRole;
import com.smart_hospital_ai.demo.exception.ApiException;
import com.smart_hospital_ai.demo.repository.UserRepository;
import com.smart_hospital_ai.demo.security.HospitalUserPrincipal;
import com.smart_hospital_ai.demo.service.DoctorAvailabilityService;
import com.smart_hospital_ai.demo.service.DoctorService;
import com.smart_hospital_ai.demo.service.PatientService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(PostgresEnvironmentCondition.class)
@Transactional
class PostgresHospitalIntegrationTest {

	@Autowired
	private PatientService patientService;

	@Autowired
	private DoctorService doctorService;

	@Autowired
	private DoctorAvailabilityService availabilityService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private MockMvc mockMvc;

	@Test
	void registrationPersistsBcryptAccountAndPatientProfile() {
		String email = uniqueEmail("patient");
		PatientResponse patient = patientService.register(new PatientRegistrationRequest(
			email, "A-long-test-password-42", "Test", "Patient", null, null));

		var account = userRepository.findByEmailIgnoreCase(email).orElseThrow();
		assertEquals(UserRole.PATIENT, account.getRole());
		assertTrue(account.getPasswordHash().startsWith("$2"));
		assertTrue(passwordEncoder.matches("A-long-test-password-42", account.getPasswordHash()));
		assertTrue(patient.medicalRecordNumber().startsWith("MRN-"));
	}

	@Test
	void persistedPatientCanAuthenticateThroughHttpBasic() throws Exception {
		String email = uniqueEmail("login");
		patientService.register(new PatientRegistrationRequest(
			email, "A-long-test-password-42", "Login", "Patient", null, null));

		mockMvc.perform(get("/api/v1/auth/me").with(httpBasic(email, "A-long-test-password-42")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.email").value(email))
			.andExpect(jsonPath("$.role").value("PATIENT"));
	}

	@Test
	void patientCannotReadAnotherPatientsProfile() {
		PatientResponse patient = patientService.register(new PatientRegistrationRequest(
			uniqueEmail("owner"), "A-long-test-password-42", "Owner", "Patient", null, null));
		PatientResponse otherPatient = patientService.register(new PatientRegistrationRequest(
			uniqueEmail("other"), "A-long-test-password-42", "Other", "Patient", null, null));
		var ownerAccount = userRepository.findByEmailIgnoreCase(patient.email()).orElseThrow();
		HospitalUserPrincipal principal = new HospitalUserPrincipal(ownerAccount);

		assertEquals(patient.id(), patientService.getById(patient.id(), principal).id());
		assertThrows(AccessDeniedException.class, () -> patientService.getById(otherPatient.id(), principal));
	}

	@Test
	void doctorAvailabilityPersistsAndRejectsOverlappingSlots() {
		String email = uniqueEmail("doctor");
		DoctorCreateRequest request = new DoctorCreateRequest(email, "A-long-test-password-42",
			"LIC-" + UUID.randomUUID(), "Test", "Doctor", "Cardiology", null);
		var doctor = doctorService.create(request);
		HospitalUserPrincipal principal = new HospitalUserPrincipal(userRepository.findByEmailIgnoreCase(email).orElseThrow());

		DoctorAvailabilityResponse saved = availabilityService.add(doctor.id(),
			new DoctorAvailabilityRequest((short) 1, LocalTime.of(9, 0), LocalTime.of(12, 0)), principal);

		assertEquals(1, availabilityService.getForDoctor(doctor.id()).size());
		assertThrows(ApiException.class, () -> availabilityService.add(doctor.id(),
			new DoctorAvailabilityRequest((short) 1, LocalTime.of(11, 0), LocalTime.of(13, 0)), principal));
		assertTrue(saved.id() > 0);
	}

	private static String uniqueEmail(String prefix) {
		return prefix + "+" + UUID.randomUUID() + "@example.test";
	}
}