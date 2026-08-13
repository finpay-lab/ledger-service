package com.finpay.ledger.service.interfaces.web;

import com.finpay.ledger.service.domain.AccountBalanceNotFoundException;
import com.finpay.ledger.service.domain.UnbalancedPostingException;
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
import org.springframework.orm.ObjectOptimisticLockingFailureException;
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
public class LedgerExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(LedgerExceptionHandler.class);

    @ExceptionHandler(AccountBalanceNotFoundException.class)
    public ResponseEntity<ProblemDetail> accountNotFound(AccountBalanceNotFoundException ex) {
        return detail(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", null);
    }

    @ExceptionHandler(UnbalancedPostingException.class)
    public ResponseEntity<ProblemDetail> unbalanced(UnbalancedPostingException ex) {
        return detail(HttpStatus.BAD_REQUEST, "UNBALANCED_POSTING",
                "A posting must balance: SUM(debit) == SUM(credit)", null);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> concurrentModification(ObjectOptimisticLockingFailureException ex) {
        // A concurrent posting committed first; the client should retry with the
        // fresh balance. The optimistic-lock check prevented a lost update.
        return detail(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION", null);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> illegalArgument(IllegalArgumentException ex) {
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
        return detail(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                "Missing required header: " + ex.getHeaderName(), null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> unexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return detail(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR.name(), null);
    }

    private ResponseEntity<ProblemDetail> detail(HttpStatus status, String code, Map<String, Object> details) {
        return detail(status, code, null, details);
    }

    private ResponseEntity<ProblemDetail> detail(HttpStatus status, String code, String message, Map<String, Object> details) {
        String traceId = MDC.get(CorrelationIdFilter.MDC_KEY);
        return ResponseEntity.status(status).body(new ProblemDetail(
                status.value(), code, message, traceId != null ? traceId : "-", details));
    }
}
