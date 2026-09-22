package io.depguard.config;

import io.depguard.shared.ResourceNotFoundException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Translates exceptions into RFC 7807 problem details.
 *
 * <p>Every body carries the Spring-native members ({@code title}, {@code detail}, {@code errors}) plus the
 * machine-readable {@code error}/{@code message} pair documented in RFC-0001 §11.3, so API clients can rely on
 * either contract.
 */
@RestControllerAdvice
class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String ERROR_VALIDATION = "VALIDATION_ERROR";

    private static final String ERROR_NOT_FOUND = "NOT_FOUND";

    private static final String ERROR_INTERNAL = "INTERNAL_ERROR";

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final Environment environment;

    GlobalExceptionHandler(Environment environment) {
        this.environment = environment;
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        LOG.warn("Validation error: {}", ex.getMessage());
        var errors = ex.getAllErrors().stream()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .toList();
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Validation Error", ERROR_VALIDATION, ex.getMessage());
        problem.setProperty("errors", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        LOG.warn("Malformed request body: {}", ex.getMessage());
        ProblemDetail problem = problem(
                HttpStatus.BAD_REQUEST, "Validation Error", ERROR_VALIDATION, "Request body is missing or malformed");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        LOG.warn("Bad request: {}", ex.getMessage());
        return withErrors(problem(HttpStatus.BAD_REQUEST, "Bad Request", ERROR_VALIDATION, ex.getMessage()));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail handleResourceNotFound(ResourceNotFoundException ex) {
        LOG.warn("Resource not found: {}", ex.getMessage());
        return withErrors(problem(HttpStatus.NOT_FOUND, "Not Found", ERROR_NOT_FOUND, ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex) {
        LOG.error("Unexpected error", ex);
        String detail = isDevelopmentMode() ? ex.getMessage() : "An unexpected error occurred";
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", ERROR_INTERNAL, detail);
    }

    private static ProblemDetail problem(HttpStatus status, String title, String errorCode, String message) {
        String detail = message == null || message.isBlank() ? title : message;
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setProperty("error", errorCode);
        problem.setProperty("message", detail);
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    private static ProblemDetail withErrors(ProblemDetail problem) {
        problem.setProperty("errors", List.of(problem.getDetail()));
        return problem;
    }

    private boolean isDevelopmentMode() {
        return Arrays.asList(environment.getActiveProfiles()).contains("local");
    }
}
