package com.smart_hospital_ai.demo.service;

import com.smart_hospital_ai.demo.entity.ReportGenerationMode;

public record GeneratedReportContent(String content, ReportGenerationMode generationMode) {
}