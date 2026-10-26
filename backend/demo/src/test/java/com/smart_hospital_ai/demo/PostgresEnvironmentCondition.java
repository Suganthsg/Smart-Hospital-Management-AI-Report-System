package com.smart_hospital_ai.demo;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

public class PostgresEnvironmentCondition implements ExecutionCondition {

	@Override
	public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
		String username = System.getenv("DB_USERNAME");
		String password = System.getenv("DB_PASSWORD");
		boolean configured = username != null && !username.isBlank()
			&& password != null && !password.isBlank();
		if (configured) {
			return ConditionEvaluationResult.enabled("PostgreSQL credentials are configured");
		}
		return ConditionEvaluationResult.disabled("Set DB_USERNAME and DB_PASSWORD to run PostgreSQL integration tests");
	}
}