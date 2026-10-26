package com.smart_hospital_ai.demo.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smart_hospital_ai.demo.config.SecurityConfig;
import com.smart_hospital_ai.demo.dto.StaffUserCreateRequest;
import com.smart_hospital_ai.demo.dto.StaffUserResponse;
import com.smart_hospital_ai.demo.entity.UserRole;
import com.smart_hospital_ai.demo.service.StaffUserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminUserController.class)
@Import(SecurityConfig.class)
class AdminUserAuthorizationTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private StaffUserService staffUserService;

	@Test
	void onlyAdminsCanProvisionStaffAccounts() throws Exception {
		when(staffUserService.create(any(StaffUserCreateRequest.class)))
			.thenReturn(new StaffUserResponse(1L, "lab@example.test", UserRole.LAB_TECHNICIAN, true));
		String body = """
			{"email":"lab@example.test","password":"Long-enough-password-42","role":"LAB_TECHNICIAN"}
			""";

		mockMvc.perform(post("/api/v1/admin/users")
				.with(user("admin").roles("ADMIN"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isCreated());
		mockMvc.perform(post("/api/v1/admin/users")
				.with(user("doctor").roles("DOCTOR"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isForbidden());
	}
}