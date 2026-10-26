package com.smart_hospital_ai.demo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.junit.jupiter.api.extension.ExtendWith;

@SpringBootTest
@ExtendWith(PostgresEnvironmentCondition.class)
class DemoApplicationTests {

	@Test
	void contextLoads() {
	}

}
