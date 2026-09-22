package io.depguard.dependency;

import io.depguard.shared.IdGenerator;
import java.io.Serializable;

/**
 * Primary key of a {@link Dependency}: a TSID (time-sorted, URL-safe, 13 characters).
 */
public record DependencyId(String id) implements Serializable {

    public DependencyId {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Dependency id must not be blank");
        }
    }

    public static DependencyId of(String id) {
        return new DependencyId(id);
    }

    public static DependencyId generate() {
        return new DependencyId(IdGenerator.generate());
    }

    @Override
    public String toString() {
        return id;
    }
}
