package io.depguard.risk;

import java.util.List;

/** Pure result of applying the Dependency Risk Heuristic. */
public record RiskDecision(int heuristicScore, RiskLevel riskLevel, RiskConfidence confidence, List<String> reasons) {}
