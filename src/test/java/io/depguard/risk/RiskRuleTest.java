package io.depguard.risk;

import static org.assertj.core.api.Assertions.assertThat;

import io.depguard.eol.EolStatus;
import io.depguard.vulnerability.Severity;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class RiskRuleTest {

    @ParameterizedTest
    @MethodSource("bucketBoundaries")
    void assignsExpectedRiskBucket(int score, RiskLevel expected) {
        assertThat(RiskRule.riskLevelFor(score)).isEqualTo(expected);
    }

    static Stream<Arguments> bucketBoundaries() {
        return Stream.of(
                Arguments.of(20, RiskLevel.LOW),
                Arguments.of(21, RiskLevel.MEDIUM),
                Arguments.of(40, RiskLevel.MEDIUM),
                Arguments.of(41, RiskLevel.HIGH),
                Arguments.of(70, RiskLevel.HIGH),
                Arguments.of(71, RiskLevel.CRITICAL));
    }

    @Test
    void givesTransitiveDependencyALowerScoreThanEquivalentDirectDependency() {
        RiskDecision direct = RiskRule.evaluate(EolStatus.EOL, List.of(Severity.HIGH), true);
        RiskDecision transitive = RiskRule.evaluate(EolStatus.EOL, List.of(Severity.HIGH), false);

        assertThat(direct.heuristicScore()).isGreaterThan(transitive.heuristicScore());
    }

    @Test
    void flagsUnknownEolDataWithLowConfidenceWithoutAddingEolPoints() {
        RiskDecision decision = RiskRule.evaluate(EolStatus.UNKNOWN, List.of(), true);

        assertThat(decision.heuristicScore()).isZero();
        assertThat(decision.riskLevel()).isEqualTo(RiskLevel.LOW);
        assertThat(decision.confidence()).isEqualTo(RiskConfidence.LOW);
        assertThat(decision.reasons()).contains("EOL status unknown — manual review recommended");
    }

    @Test
    void treatsUnknownAdvisorySeverityConservatively() {
        RiskDecision decision = RiskRule.evaluate(EolStatus.SUPPORTED, List.of(Severity.UNKNOWN), true);

        assertThat(decision.heuristicScore()).isEqualTo(20);
        assertThat(decision.confidence()).isEqualTo(RiskConfidence.MEDIUM);
    }
}
