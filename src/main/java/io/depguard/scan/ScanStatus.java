package io.depguard.scan;

/**
 * Lifecycle of a scan.
 */
public enum ScanStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED
}
