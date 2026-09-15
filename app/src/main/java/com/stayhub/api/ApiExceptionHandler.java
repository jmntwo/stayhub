package com.stayhub.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(IllegalArgumentException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ErrorResponse invalidQuery(IllegalArgumentException e) {
		return new ErrorResponse("INVALID_REQUEST", e.getMessage());
	}

	@ExceptionHandler(MissingServletRequestParameterException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ErrorResponse missingParameter(MissingServletRequestParameterException e) {
		return new ErrorResponse("INVALID_REQUEST", e.getParameterName() + " is required");
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ErrorResponse typeMismatch(MethodArgumentTypeMismatchException e) {
		return new ErrorResponse("INVALID_REQUEST", e.getName() + " has invalid format");
	}
}
