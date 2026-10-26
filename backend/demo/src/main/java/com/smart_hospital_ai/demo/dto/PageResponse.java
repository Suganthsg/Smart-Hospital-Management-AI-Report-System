package com.smart_hospital_ai.demo.dto;

import java.util.List;
import org.springframework.data.domain.Page;

public record PageResponse<T>(List<T> content, int page, int size,
		long totalElements, int totalPages, boolean last) {

	public static <T> PageResponse<T> from(Page<T> result) {
		return new PageResponse<>(result.getContent(), result.getNumber(), result.getSize(),
			result.getTotalElements(), result.getTotalPages(), result.isLast());
	}
}