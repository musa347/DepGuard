package io.depguard.scan;

/**
 * Response payload of {@code POST /api/projects/{id}/scans}: the identifier to poll for the scan result.
 */
public record ScanCreatedResponse(String scanId) {}
