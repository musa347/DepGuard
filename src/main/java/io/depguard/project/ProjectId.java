package io.depguard.project;

import io.depguard.shared.IdGenerator;
import java.io.Serializable;

/**
 * Primary key of a {@link Project}: a TSID (time-sorted, URL-safe, 13 characters).
 */
public record ProjectId(String id) implements Serializable {

    public ProjectId {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Project id must not be blank");
        }
    }

    public static ProjectId of(String id) {
        return new ProjectId(id);
    }

    public static ProjectId generate() {
        return new ProjectId(IdGenerator.generate());
    }

    @Override
    public String toString() {
        return id;
    }
}
