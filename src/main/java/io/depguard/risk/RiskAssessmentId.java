package io.depguard.risk;

import java.io.Serializable;

/** Composite identity of a dependency assessment within a scan. */
public record RiskAssessmentId(String scanId, String dependencyId) implements Serializable {}
