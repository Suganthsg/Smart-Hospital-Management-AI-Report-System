package com.smart_hospital_ai.demo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smart_hospital_ai.demo.entity.UserAccount;
import com.smart_hospital_ai.demo.entity.UserRole;
import com.smart_hospital_ai.demo.exception.ApiException;
import com.smart_hospital_ai.demo.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class UserAccountServiceTest {

	private final UserRepository userRepository = mock(UserRepository.class);
	private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
	private final UserAccountService service = new UserAccountService(userRepository, passwordEncoder);

	@Test
	void normalizesEmailAndStoresOnlyBcryptPasswordHash() {
		when(userRepository.existsByEmailIgnoreCase("user@example.test")).thenReturn(false);
		when(userRepository.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

		service.create(" User@Example.Test ", "A-long-test-password-42", UserRole.PATIENT);

		ArgumentCaptor<UserAccount> accountCaptor = ArgumentCaptor.forClass(UserAccount.class);
		verify(userRepository).save(accountCaptor.capture());
		UserAccount savedAccount = accountCaptor.getValue();
		assertEquals("user@example.test", savedAccount.getEmail());
		assertEquals(UserRole.PATIENT, savedAccount.getRole());
		assertNotEquals("A-long-test-password-42", savedAccount.getPasswordHash());
		assertTrue(passwordEncoder.matches("A-long-test-password-42", savedAccount.getPasswordHash()));
	}

	@Test
	void rejectsPasswordsLongerThanBcryptByteLimit() {
		assertThrows(ApiException.class,
			() -> service.create("user@example.test", "a".repeat(73), UserRole.PATIENT));
}
}