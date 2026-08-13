package com.finpay.ledger.service.interfaces.web;

import com.finpay.ledger.service.domain.IllegalStateTransitionException;
import com.finpay.ledger.service.domain.PostingNotFoundException;
import com.finpay.common.web.error.ErrorCode;
import com.finpay.common.web.error.ProblemDetail;
import com.finpay.common.web.filter.CorrelationIdFilter;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.Map;

/**
 * Maps exceptions to RFC-9457 problem details (com.finpay.common.web). No
 * business logic here — only transport error mapping. Never leaks internal
 * exception text.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(PostingNotFoundException.class)
    public ResponseEntity<ProblemDetail> postingNotFound(PostingNotFoundException ex) {
        return detail(HttpStatus.NOT_FOUND, "POSTING_NOT_FOUND", "Referenced posting does not exist", null);
    }

    @ExceptionHandler(IllegalStateTransitionException.class)
    public ResponseEntity<ProblemDetail> illegalTransition(IllegalStateTransitionException ex) {
        return detail(HttpStatus.CONFLICT, ErrorCode.INVALID_STATE_TRANSITION,
                Map.of("from", ex.from().name(), "to", ex.to().name()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> illegalArgument(IllegalArgumentException ex) {
        // Domain/boundary validation failure (e.g. imbalanced posting).
        return detail(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", ex.getMessage(), null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> validation(MethodArgumentNotValidException ex) {
        List<String> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .toList();
        return detail(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed", Map.of("errors", errors));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> constraintViolation(ConstraintViolationException ex) {
        return detail(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed", null);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> unreadable(HttpMessageNotReadableException ex) {
        return detail(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Malformed request body", null);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ProblemDetail> missingHeader(MissingRequestHeaderException ex) {
        return detail(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Missing required header: " + ex.getHeaderName(), null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> unexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return detail(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, null);
    }

    private ResponseEntity<ProblemDetail> detail(HttpStatus status, ErrorCode code, Map<String, Object> details) {
        return detail(status, code.name(), code.defaultMessage(), details);
    }

    private ResponseEntity<ProblemDetail> detail(HttpStatus status, String code, String message, Map<String, Object> details) {
        String traceId = MDC.get(CorrelationIdFilter.MDC_KEY);
        return ResponseEntity.status(status).body(new ProblemDetail(
                status.value(), code, message, traceId != null ? traceId : "-", details));
    }
}