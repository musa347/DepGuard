package io.depguard.risk;

import io.depguard.eol.EolStatus;
import io.depguard.vulnerability.Severity;
import java.util.ArrayList;
import java.util.List;

/**
 * The deterministic Dependency Risk Heuristic.
 *
 * <p>EOL unknowns intentionally contribute no points, because their state cannot be established,
 * but always lower confidence. Unknown advisory severities are scored conservatively.
 */
public final class RiskRule {

    private RiskRule() {}

    public static RiskDecision evaluate(EolStatus eolStatus, List<Severity> severities, boolean direct) {
        List<String> reasons = new ArrayList<>();
        int score = eolPoints(eolStatus, reasons);
        RiskConfidence confidence = eolStatus == EolStatus.UNKNOWN ? RiskConfidence.LOW : RiskConfidence.HIGH;

        for (Severity severity : severities) {
            score += advisoryPoints(severity, reasons);
            if (severity == Severity.UNKNOWN && confidence != RiskConfidence.LOW) {
                confidence = RiskConfidence.MEDIUM;
            }
        }

        double multiplier = direct ? 1.0 : 0.6;
        reasons.add(direct ? "Direct dependency (×1.0)" : "Transitive dependency (×0.6)");
        int heuristicScore = (int) Math.round(score * multiplier);
        return new RiskDecision(heuristicScore, riskLevelFor(heuristicScore), confidence, List.copyOf(reasons));
    }

    private static int eolPoints(EolStatus status, List<String> reasons) {
        return switch (status) {
            case EOL -> points(30, "EOL (+30)", reasons);
            case MAINTENANCE -> points(10, "Maintenance (+10)", reasons);
            case UNKNOWN -> {
                reasons.add("EOL status unknown — manual review recommended");
                yield 0;
            }
            case SUPPORTED -> 0;
        };
    }

    private static int advisoryPoints(Severity severity, List<String> reasons) {
        return switch (severity) {
            case CRITICAL -> points(40, "Advisory CRITICAL (+40)", reasons);
            case HIGH -> points(30, "Advisory HIGH (+30)", reasons);
            case MEDIUM -> points(15, "Advisory MEDIUM (+15)", reasons);
            case LOW -> points(5, "Advisory LOW (+5)", reasons);
            case UNKNOWN -> points(20, "Advisory severity unknown (+20)", reasons);
        };
    }

    private static int points(int points, String reason, List<String> reasons) {
        reasons.add(reason);
        return points;
    }

    static RiskLevel riskLevelFor(int score) {
        if (score <= 20) {
            return RiskLevel.LOW;
        }
        if (score <= 40) {
            return RiskLevel.MEDIUM;
        }
        if (score <= 70) {
            return RiskLevel.HIGH;
        }
        return RiskLevel.CRITICAL;
    }
}
