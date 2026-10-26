package com.smart_hospital_ai.demo.service;

import java.time.LocalDate;
import java.util.List;
import com.smart_hospital_ai.demo.dto.VerifiedLabResultInput;

public record ReportGenerationInput(LocalDate encounterDate, String recordedDiagnosis,
		String recordedClinicalNotes, List<VerifiedLabResultInput> verifiedLabResults) {
	public ReportGenerationInput {
		verifiedLabResults = List.copyOf(verifiedLabResults);
	}
}