package io.depguard.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class GitHubUrlValidatorTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
                    https://github.com/spring-projects/spring-boot|https://github.com/spring-projects/spring-boot
                    https://github.com/spring-projects/spring-boot/|https://github.com/spring-projects/spring-boot
                    https://github.com/spring-projects/spring-boot.git|https://github.com/spring-projects/spring-boot
                    https://github.com/spring-projects/spring-boot.git/|https://github.com/spring-projects/spring-boot
                    https://github.com/spring-projects/spring-boot.GIT|https://github.com/spring-projects/spring-boot
                    HTTPS://GitHub.COM/spring-projects/spring-boot|https://github.com/spring-projects/spring-boot
                    https://github.com/depguard/.github|https://github.com/depguard/.github
                    https://github.com/depguard/repo_name-1.2|https://github.com/depguard/repo_name-1.2
                    """)
    void normalisesPublicGitHubUrls(String url, String expected) {
        assertThat(GitHubUrlValidator.validateAndNormalize(url)).isEqualTo(expected);
    }

    @Test
    void trimsSurroundingWhitespace() {
        assertThat(GitHubUrlValidator.validateAndNormalize("  https://github.com/depguard/core  "))
                .isEqualTo("https://github.com/depguard/core");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void rejectsBlankUrls(String url) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> GitHubUrlValidator.validateAndNormalize(url))
                .withMessageContaining("must not be blank");
    }

    @Test
    void rejectsUrlsThatExceedTheStorageLimit() {
        String tooLong = "https://github.com/depguard/" + "a".repeat(600);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> GitHubUrlValidator.validateAndNormalize(tooLong))
                .withMessageContaining("must not exceed 512 characters");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                // not plain HTTPS
                "http://github.com/depguard/core",
                "git://github.com/depguard/core",
                "ssh://git@github.com/depguard/core.git",
                "git@github.com:depguard/core.git",
                "github.com/depguard/core",
                // other or look-alike hosts
                "https://gitlab.com/depguard/core",
                "https://bitbucket.org/depguard/core",
                "https://github.com.evil.com/depguard/core",
                "https://www.github.com/depguard/core",
                "https://evil.com/github.com/depguard/core",
                // not a repository root
                "https://github.com/",
                "https://github.com/depguard",
                "https://github.com/depguard/",
                "https://github.com/depguard/core/tree/main",
                "https://github.com/depguard/core/issues",
                "https://github.com/settings/core",
                "https://github.com//depguard/core",
                "https://github.com/depguard/core?tab=readme",
                "https://github.com/depguard/core#readme",
                // credentials, ports and encoded or traversal-looking paths
                "https://user:token@github.com/depguard/core",
                "https://github.com:8443/depguard/core",
                "https://github.com/depguard/core%2F..%2Fother",
                "https://github.com/../etc/passwd",
                "https://github.com/depguard/..",
                "https://github.com/depguard/.",
                "https://github.com/depguard/.git",
                "https://github.com/-depguard/core",
                "https://github.com/depguard/core name",
                "https://github.com/depguard/co re",
                "not a url",
                "/depguard/core"
            })
    void rejectsAnythingButAPublicGitHubRepositoryUrl(String url) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> GitHubUrlValidator.validateAndNormalize(url))
                .withMessage(GitHubUrlValidator.REJECTION_MESSAGE);
    }
}
