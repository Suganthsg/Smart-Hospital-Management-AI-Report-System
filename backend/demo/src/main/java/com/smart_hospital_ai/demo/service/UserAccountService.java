package com.smart_hospital_ai.demo.service;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import com.smart_hospital_ai.demo.entity.UserAccount;
import com.smart_hospital_ai.demo.entity.UserRole;
import com.smart_hospital_ai.demo.exception.ApiException;
import com.smart_hospital_ai.demo.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserAccountService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	public UserAccountService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
	}

	public UserAccount create(String email, String password, UserRole role) {
		String normalizedEmail = normalizeEmail(email);
		if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Password is too long");
		}
		if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
			throw new ApiException(HttpStatus.CONFLICT, "An account with this email already exists");
		}
		return userRepository.save(new UserAccount(normalizedEmail, passwordEncoder.encode(password), role));
	}

	public static String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}
}