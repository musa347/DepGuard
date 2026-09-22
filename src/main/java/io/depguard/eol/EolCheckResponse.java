package io.depguard.eol;

/** Response payload of {@code POST /api/scans/{id}/eol-check}. */
public record EolCheckResponse(String scanId) {}
