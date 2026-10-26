package com.smart_hospital_ai.demo.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import com.smart_hospital_ai.demo.dto.VerifiedLabResultInput;
import com.smart_hospital_ai.demo.entity.ReportGenerationMode;
import org.junit.jupiter.api.Test;

class MockReportGenerationServiceTest {

	private final MockReportGenerationService service = new MockReportGenerationService();

	@Test
	void includesOnlyPassedEncounterAndVerifiedLabValues() {
		ReportGenerationInput input = new ReportGenerationInput(LocalDate.of(2026, 10, 20),
			"Recorded diagnosis", "Recorded symptoms and notes", List.of(
				new VerifiedLabResultInput("Sodium", "140", "mmol/L", "135-145",
					OffsetDateTime.parse("2026-10-20T10:15:00Z"))));

		GeneratedReportContent generated = service.generate(input);

		assertTrue(generated.content().contains("Recorded diagnosis"));
		assertTrue(generated.content().contains("Recorded symptoms and notes"));
		assertTrue(generated.content().contains("Sodium: 140 mmol/L"));
		assertTrue(generated.content().contains("Recorded reference range: 135-145"));
		assertEquals(ReportGenerationMode.MOCK, generated.generationMode());
		assertTrue(generated.content().contains("draft"));
		assertFalse(generated.content().contains("Medication recommendation"));
	}

	@Test
	void doesNotInventMissingLabValuesOrReferenceRanges() {
		GeneratedReportContent generated = service.generate(new ReportGenerationInput(
			LocalDate.of(2026, 10, 20), "Diagnosis as recorded", "Notes as recorded", List.of()));

		assertTrue(generated.content().contains("No verified laboratory results recorded"));
		assertFalse(generated.content().contains("Reference range:"));
		assertFalse(generated.content().contains("0 mmol/L"));
	}
}