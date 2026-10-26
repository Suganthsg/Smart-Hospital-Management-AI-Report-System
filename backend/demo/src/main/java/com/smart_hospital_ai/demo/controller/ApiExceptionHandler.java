package com.smart_hospital_ai.demo.controller;

import java.util.stream.Collectors;
import com.smart_hospital_ai.demo.dto.ApiErrorResponse;
import com.smart_hospital_ai.demo.exception.ApiException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(ApiException.class)
	ResponseEntity<ApiErrorResponse> handleApiException(ApiException exception) {
		return ResponseEntity.status(exception.getStatus())
			.body(new ApiErrorResponse(exception.getStatus().getReasonPhrase(), exception.getMessage()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ApiErrorResponse> handleValidationFailure(MethodArgumentNotValidException exception) {
		String message = exception.getBindingResult().getFieldErrors().stream()
			.map(error -> error.getField() + ": " + error.getDefaultMessage())
			.distinct()
			.collect(Collectors.joining("; "));
		if (message.isBlank()) {
			message = "Request validation failed";
		}
		return ResponseEntity.badRequest()
			.body(new ApiErrorResponse("Bad Request", message));
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	ResponseEntity<ApiErrorResponse> handleDataConflict() {
		return ResponseEntity.status(HttpStatus.CONFLICT)
			.body(new ApiErrorResponse("Conflict", "A record with the supplied unique value already exists"));
	}
}