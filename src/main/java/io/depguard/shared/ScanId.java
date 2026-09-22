package io.depguard.shared;

import java.io.Serializable;

/**
 * Primary key of a scan: a TSID (time-sorted, URL-safe, 13 characters).
 */
public record ScanId(String id) implements Serializable {

    public ScanId {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Scan id must not be blank");
        }
    }

    public static ScanId of(String id) {
        return new ScanId(id);
    }

    public static ScanId generate() {
        return new ScanId(IdGenerator.generate());
    }

    @Override
    public String toString() {
        return id;
    }
}
