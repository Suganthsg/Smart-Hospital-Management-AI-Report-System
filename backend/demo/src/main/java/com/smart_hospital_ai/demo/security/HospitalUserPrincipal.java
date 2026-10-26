package com.smart_hospital_ai.demo.security;

import java.util.Collection;
import java.util.List;
import com.smart_hospital_ai.demo.entity.UserAccount;
import com.smart_hospital_ai.demo.entity.UserRole;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class HospitalUserPrincipal implements UserDetails {

	private final Long userId;
	private final String email;
	private final String passwordHash;
	private final UserRole role;
	private final boolean active;

	public HospitalUserPrincipal(UserAccount user) {
		this.userId = user.getId();
		this.email = user.getEmail();
		this.passwordHash = user.getPasswordHash();
		this.role = user.getRole();
		this.active = user.isActive();
	}

	public Long getUserId() {
		return userId;
	}

	public UserRole getRole() {
		return role;
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
	}

	@Override
	public String getPassword() {
		return passwordHash;
	}

	@Override
	public String getUsername() {
		return email;
	}

	@Override
	public boolean isEnabled() {
		return active;
	}
}