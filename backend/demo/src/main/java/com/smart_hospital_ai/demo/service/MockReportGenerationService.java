package com.smart_hospital_ai.demo.service;

import java.util.StringJoiner;
import com.smart_hospital_ai.demo.dto.VerifiedLabResultInput;
import com.smart_hospital_ai.demo.entity.ReportGenerationMode;
import org.springframework.stereotype.Service;

@Service
public class MockReportGenerationService implements ReportGenerationService {

	@Override
	public GeneratedReportContent generate(ReportGenerationInput input) {
		StringBuilder content = new StringBuilder()
			.append("Clinical report draft (mock; recorded data only)\n\n")
			.append("Encounter date: ").append(input.encounterDate()).append("\n\n")
			.append("Recorded diagnosis:\n").append(input.recordedDiagnosis()).append("\n\n")
			.append("Recorded clinical notes:\n").append(input.recordedClinicalNotes()).append("\n\n")
			.append("Verified laboratory results:\n");

		if (input.verifiedLabResults().isEmpty()) {
			content.append("No verified laboratory results recorded for this encounter.\n");
		} else {
			for (VerifiedLabResultInput result : input.verifiedLabResults()) {
				StringJoiner line = new StringJoiner("; ");
				line.add(result.testName() + ": " + result.resultValue()
					+ (isPresent(result.units()) ? " " + result.units() : ""));
				if (isPresent(result.referenceRange())) {
					line.add("Recorded reference range: " + result.referenceRange());
				}
				if (result.verifiedAt() != null) {
					line.add("Verified at: " + result.verifiedAt());
				}
				content.append("- ").append(line).append("\n");
			}
		}

		return new GeneratedReportContent(content.toString(), ReportGenerationMode.MOCK);
	}

	private static boolean isPresent(String value) {
		return value != null && !value.isBlank();
	}
}