package io.depguard.remediation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CompatibilityAdvisorTest {

    @Test
    void prefersSameMajorUpgradesAsCompatible() {
        assertThat(CompatibilityAdvisor.isCompatibleUpgrade("2.7.18", "2.7.25")).isTrue();
        assertThat(CompatibilityAdvisor.upgradeType("2.7.18", "2.7.25")).isEqualTo(UpgradeType.PATCH);
        assertThat(CompatibilityAdvisor.upgradeType("2.7.18", "2.8.0")).isEqualTo(UpgradeType.MINOR);
    }

    @Test
    void documentsSpringBootMajorMigration() {
        assertThat(CompatibilityAdvisor.isCompatibleUpgrade("2.7.18", "3.4.1")).isFalse();
        assertThat(CompatibilityAdvisor.upgradeType("2.7.18", "3.4.1")).isEqualTo(UpgradeType.MAJOR);
        assertThat(CompatibilityAdvisor.breakingChangeSummary("org.springframework.boot", "2.7.18", "3.4.1"))
                .contains("javax → jakarta", "Java 17+");
    }
}
