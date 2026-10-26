package com.smart_hospital_ai.demo.dto;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

public record PrescriptionCreateRequest(@NotEmpty List<@Valid PrescriptionItemRequest> items) {
}