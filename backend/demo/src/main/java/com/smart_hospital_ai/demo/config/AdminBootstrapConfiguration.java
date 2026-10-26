package com.smart_hospital_ai.demo.config;

import java.util.Locale;
import java.nio.charset.StandardCharsets;
import com.smart_hospital_ai.demo.entity.UserAccount;
import com.smart_hospital_ai.demo.entity.UserRole;
import com.smart_hospital_ai.demo.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AdminBootstrapConfiguration {

	@Bean
	ApplicationRunner bootstrapAdmin(UserRepository userRepository, PasswordEncoder passwordEncoder,
			@Value("${BOOTSTRAP_ADMIN_EMAIL:}") String configuredEmail,
			@Value("${BOOTSTRAP_ADMIN_PASSWORD:}") String configuredPassword) {
		return args -> {
			if (configuredEmail.isBlank() && configuredPassword.isBlank()) {
				return;
			}
			if (configuredEmail.isBlank() || configuredPassword.isBlank()) {
				throw new IllegalStateException("Both bootstrap admin environment variables must be configured");
			}
			if (configuredPassword.length() < 12
					|| configuredPassword.getBytes(StandardCharsets.UTF_8).length > 72) {
				throw new IllegalStateException("Bootstrap admin password must be at least 12 characters and at most 72 bytes");
			}
			if (userRepository.existsByRoleAndActiveTrue(UserRole.ADMIN)) {
				return;
			}

			String email = configuredEmail.trim().toLowerCase(Locale.ROOT);
			if (userRepository.existsByEmailIgnoreCase(email)) {
				throw new IllegalStateException("Bootstrap admin email is already assigned to another account");
			}
			userRepository.save(new UserAccount(email, passwordEncoder.encode(configuredPassword), UserRole.ADMIN));
		};
	}
}