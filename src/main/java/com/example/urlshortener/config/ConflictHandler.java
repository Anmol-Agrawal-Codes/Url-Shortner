package com.example.urlshortener.config;

import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ConflictHandler {

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail conflict(DataIntegrityViolationException exception) {
        String message = String.valueOf(exception.getMostSpecificCause().getMessage()).toLowerCase(Locale.ROOT);
        if (message.contains("uk_url_code")) {
            return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Alias already exists");
        }
        if (message.contains("uk_user_username")) {
            return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Username already exists");
        }
        throw exception;
    }
}
