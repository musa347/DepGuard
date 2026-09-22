package io.depguard.eol;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DefaultEolMappingStrategy}.
 *
 * <p>Uses the classpath {@code eol-mappings.yml} via Spring context loading; each test reloads the
 * strategy through a minimal Spring configuration so the YAML is picked up fresh.
 */
class DefaultEolMappingStrategyTest {

    private final DefaultEolMappingStrategy strategy =
            new DefaultEolMappingStrategy(new io.depguard.test.TestResourceLoader());

    @Test
    void resolvesSpringBootGroupIdToSpringBootProduct() {
        Optional<ProductCycle> result = strategy.resolve("org.springframework.boot", "spring-boot-starter", "2.7.18");
        assertThat(result).isPresent();
        assertThat(result.get().product()).isEqualTo("spring-boot");
        assertThat(result.get().cycle()).isEqualTo("2.7");
    }

    @Test
    void resolvesSpringFrameworkGroupIdToSpringFrameworkProduct() {
        Optional<ProductCycle> result = strategy.resolve("org.springframework", "spring-core", "5.3.20");
        assertThat(result).isPresent();
        assertThat(result.get().product()).isEqualTo("spring-framework");
        assertThat(result.get().cycle()).isEqualTo("5.3");
    }

    @Test
    void resolvesHibernateOrmGroupIdToHibernateProduct() {
        Optional<ProductCycle> result = strategy.resolve("org.hibernate.orm", "hibernate-core", "6.2.7.Final");
        assertThat(result).isPresent();
        assertThat(result.get().product()).isEqualTo("hibernate-orm");
        assertThat(result.get().cycle()).isEqualTo("6.2");
    }

    @Test
    void resolvesTomcatGroupIdToTomcatProduct() {
        Optional<ProductCycle> result = strategy.resolve("org.apache.tomcat.embed", "tomcat-embed-core", "10.1.18");
        assertThat(result).isPresent();
        assertThat(result.get().product()).isEqualTo("tomcat");
        assertThat(result.get().cycle()).isEqualTo("10.1");
    }

    @Test
    void returnsEmptyForUnrecognisedGroupId() {
        Optional<ProductCycle> result = strategy.resolve("com.example", "my-lib", "1.0.0");
        assertThat(result).isEmpty();
    }

    @Test
    void handlesSingleComponentVersion() {
        Optional<ProductCycle> result = strategy.resolve("org.springframework.boot", "spring-boot-starter", "3");
        assertThat(result).isPresent();
        assertThat(result.get().cycle()).isEqualTo("3");
    }

    @Test
    void returnsEmptyForBlankVersion() {
        Optional<ProductCycle> result = strategy.resolve("org.springframework.boot", "spring-boot-starter", "");
        assertThat(result).isEmpty();
    }

    @Test
    void doesNotMatchOnPartialGroupIdContainment() {
        // "org.springframework.boot" prefix must be an actual start-of-string match
        Optional<ProductCycle> result =
                strategy.resolve("my.org.springframework.boot", "spring-boot-starter", "2.7.18");
        // startsWith is used, so this depends on the mapping prefix — "my.org.springframework.boot" does not start with
        // "org.springframework.boot"
        assertThat(result).isEmpty();
    }
}
