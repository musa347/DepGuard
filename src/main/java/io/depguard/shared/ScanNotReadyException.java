package io.depguard.shared;

/** Raised when a report is requested before its scan has finished successfully. */
public class ScanNotReadyException extends RuntimeException {

    public ScanNotReadyException(String message) {
        super(message);
    }
}
