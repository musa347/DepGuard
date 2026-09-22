package io.depguard.shared;

/**
 * Thrown when a requested domain resource (project, scan, report, …) does not exist.
 *
 * <p>{@code GlobalExceptionHandler} maps it to {@code 404 Not Found} with error code {@code NOT_FOUND}.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
