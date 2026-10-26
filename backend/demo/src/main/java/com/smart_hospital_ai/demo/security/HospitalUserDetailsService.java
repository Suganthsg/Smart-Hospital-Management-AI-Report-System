package com.smart_hospital_ai.demo.security;

import java.util.Locale;
import com.smart_hospital_ai.demo.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class HospitalUserDetailsService implements UserDetailsService {

	private final UserRepository userRepository;

	public HospitalUserDetailsService(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	@Override
	public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
		String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
		return userRepository.findByEmailIgnoreCase(normalizedEmail)
			.map(HospitalUserPrincipal::new)
			.orElseThrow(() -> new UsernameNotFoundException("Invalid email or password"));
	}
}