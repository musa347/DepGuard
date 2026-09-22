package io.depguard.eol;

import jakarta.persistence.Embeddable;
import java.io.Serializable;

/**
 * Composite primary key of an {@link EolRecord}: the scanned dependency and the owning scan.
 *
 * <p>{@code @Embeddable} is required by JPA for use with {@code @EmbeddedId}.
 * {@code Serializable} is required by the JPA spec for composite keys.
 */
@Embeddable
public record EolRecordId(String dependencyId, String scanId) implements Serializable {}
