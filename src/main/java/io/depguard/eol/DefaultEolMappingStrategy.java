package io.depguard.eol;

import io.depguard.shared.AssertUtil;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

/** Default {@link EolMappingStrategy} that loads group-id → product mappings from {@code eol-mappings.yml}. */
@Component
public class DefaultEolMappingStrategy implements EolMappingStrategy {

    private final List<EolMapping> mappings;

    public DefaultEolMappingStrategy(ResourceLoader resourceLoader) {
        Resource resource = resourceLoader.getResource("classpath:eol-mappings.yml");
        AssertUtil.requireNotNull(resource, "eol-mappings.yml must exist on the classpath");
        try (InputStream is = resource.getInputStream()) {
            Yaml yaml = new Yaml();
            Map<String, Object> config = yaml.load(is);
            List<EolMapping> loaded = new ArrayList<>();
            Object rawMappings = config.get("mappings");
            if (rawMappings instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> map) {
                        loaded.add(new EolMapping((String) map.get("groupIdPrefix"), (String) map.get("product")));
                    }
                }
            }
            this.mappings = List.copyOf(loaded);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load eol-mappings.yml", ex);
        }
    }

    @Override
    public Optional<ProductCycle> resolve(String groupId, String artifactId, String version) {
        for (EolMapping mapping : mappings) {
            if (groupId != null && groupId.startsWith(mapping.groupIdPrefix())) {
                String product = mapping.product();
                String cycle = majorMinorCycle(version);
                return cycle != null ? Optional.of(new ProductCycle(product, cycle)) : Optional.empty();
            }
        }
        return Optional.empty();
    }

    /** Extracts the major.minor cycle from a version string (e.g. "2.7.18" → "2.7"). */
    private static String majorMinorCycle(String version) {
        if (version == null || version.isBlank()) {
            return null;
        }
        String cleaned = version.replaceAll("[^\\d.]", "");
        int firstDot = cleaned.indexOf('.');
        if (firstDot < 0) {
            return cleaned; // single component (e.g. "3") — use as-is
        }
        int secondDot = cleaned.indexOf('.', firstDot + 1);
        if (secondDot < 0) {
            return cleaned; // single component after major — use as-is
        }
        return cleaned.substring(0, secondDot);
    }

    private record EolMapping(String groupIdPrefix, String product) {}
}
