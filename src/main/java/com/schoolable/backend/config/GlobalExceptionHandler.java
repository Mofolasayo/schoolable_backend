package com.schoolable.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Global Exception Handler
 * Provides consistent error responses across all controllers.
 * Logs errors with correlation IDs for tracing.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handle validation errors
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationErrors(
            MethodArgumentNotValidException ex, WebRequest request) {
        
        String correlationId = getOrCreateCorrelationId();
        
        List<String> errors = ex.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(FieldError::getDefaultMessage)
            .toList();

        log.warn("[{}] Validation failed: {}", correlationId, errors);

        return buildErrorResponse(
            HttpStatus.BAD_REQUEST,
            "VALIDATION_ERROR",
            "Validation failed",
            errors,
            correlationId
        );
    }

    /**
     * Handle authentication errors
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthenticationException(
            AuthenticationException ex, WebRequest request) {
        
        String correlationId = getOrCreateCorrelationId();
        log.warn("[{}] Authentication failed: {}", correlationId, ex.getMessage());

        return buildErrorResponse(
            HttpStatus.UNAUTHORIZED,
            "AUTHENTICATION_ERROR",
            "Authentication failed",
            null,
            correlationId
        );
    }

    /**
     * Handle authorization errors
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDeniedException(
            AccessDeniedException ex, WebRequest request) {
        
        String correlationId = getOrCreateCorrelationId();
        log.warn("[{}] Access denied: {}", correlationId, ex.getMessage());

        return buildErrorResponse(
            HttpStatus.FORBIDDEN,
            "ACCESS_DENIED",
            "You don't have permission to access this resource",
            null,
            correlationId
        );
    }

    /**
     * Handle IllegalArgumentException
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex, WebRequest request) {
        
        String correlationId = getOrCreateCorrelationId();
        log.warn("[{}] Illegal argument: {}", correlationId, ex.getMessage());

        return buildErrorResponse(
            HttpStatus.BAD_REQUEST,
            "BAD_REQUEST",
            ex.getMessage(),
            null,
            correlationId
        );
    }

    /**
     * Handle all other exceptions
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleAllExceptions(
            Exception ex, WebRequest request) {
        
        String correlationId = getOrCreateCorrelationId();
        
        // Log full stack trace for unexpected errors
        log.error("[{}] Unexpected error: {}", correlationId, ex.getMessage(), ex);

        return buildErrorResponse(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "INTERNAL_ERROR",
            "An unexpected error occurred. Reference: " + correlationId,
            null,
            correlationId
        );
    }

    private ResponseEntity<ApiErrorResponse> buildErrorResponse(
            HttpStatus status,
            String errorCode,
            String message,
            List<String> details,
            String correlationId) {
        
        ApiErrorResponse error = new ApiErrorResponse(
            status.value(),
            errorCode,
            message,
            details,
            correlationId,
            LocalDateTime.now().toString()
        );

        return ResponseEntity.status(status).body(error);
    }

    private String getOrCreateCorrelationId() {
        String correlationId = MDC.get("correlationId");
        if (correlationId == null || correlationId.isEmpty()) {
            correlationId = UUID.randomUUID().toString().substring(0, 8);
            MDC.put("correlationId", correlationId);
        }
        return correlationId;
    }

    /**
     * Structured error response
     */
    public record ApiErrorResponse(
        int status,
        String errorCode,
        String message,
        List<String> details,
        String correlationId,
        String timestamp
    ) {}
}
