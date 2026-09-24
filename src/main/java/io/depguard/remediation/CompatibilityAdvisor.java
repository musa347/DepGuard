package io.depguard.remediation;

import io.depguard.shared.AssertUtil;

/** Knows the major-version breaking boundaries that need explicit migration guidance. */
public final class CompatibilityAdvisor {

    private CompatibilityAdvisor() {}

    public static boolean isCompatibleUpgrade(String currentVersion, String candidateVersion) {
        return majorVersion(currentVersion) == majorVersion(candidateVersion);
    }

    public static UpgradeType upgradeType(String currentVersion, String candidateVersion) {
        int currentMajor = majorVersion(currentVersion);
        int candidateMajor = majorVersion(candidateVersion);
        if (candidateMajor != currentMajor) return UpgradeType.MAJOR;
        return minorVersion(currentVersion) == minorVersion(candidateVersion) ? UpgradeType.PATCH : UpgradeType.MINOR;
    }

    public static String breakingChangeSummary(String groupId, String currentVersion, String candidateVersion) {
        if (isCompatibleUpgrade(currentVersion, candidateVersion)) return null;
        if (groupId.startsWith("org.springframework.boot")) {
            return "Spring Boot 2.x → 3.x requires javax → jakarta migration and Java 17+.";
        }
        if (groupId.startsWith("org.hibernate")) {
            return "Hibernate 5.x → 6.x requires javax.persistence → jakarta.persistence migration.";
        }
        if (groupId.startsWith("org.springframework")) {
            return "Spring Framework 5.x → 6.x requires Jakarta EE 9+ compatibility.";
        }
        return "Major-version upgrade; review the dependency's migration guide for breaking changes.";
    }

    private static int majorVersion(String version) {
        return versionPart(version, 0);
    }

    private static int minorVersion(String version) {
        return versionPart(version, 1);
    }

    private static int versionPart(String version, int index) {
        AssertUtil.requireNotBlank(version, "Version must not be blank");
        String[] parts = version.replaceFirst("^[^0-9]*", "").split("\\.");
        if (parts.length <= index) return 0;
        String digits = parts[index].replaceAll("[^0-9].*", "");
        return digits.isBlank() ? 0 : Integer.parseInt(digits);
    }
}
