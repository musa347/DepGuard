package io.depguard.eol;

import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

/**
 * Loads the embedded {@code eol-fallback.yml} dataset and provides fast lookup by product + cycle.
 *
 * <p>Used by {@link EolService} when the live endoflife.date API is unavailable. Data is loaded
 * once at startup and held in memory — the file is small (under 200 entries).
 *
 * <p>If the YAML file cannot be loaded the store starts empty and logs a warning; the application
 * continues to operate, returning {@code UNKNOWN} for any product/cycle not found.
 */
@Component
public class EolFallbackStore {

    private static final Logger LOG = LoggerFactory.getLogger(EolFallbackStore.class);

    /** Key: "product/cycle" → EolInfo snapshot derived from the YAML data. */
    private final Map<String, EolInfo> entries;

    public EolFallbackStore(ResourceLoader resourceLoader) {
        this.entries = load(resourceLoader);
    }

    /**
     * Looks up the fallback EOL info for the given product and cycle.
     *
     * @return the embedded EOL info, or empty if the product/cycle is not in the fallback dataset
     */
    public Optional<EolInfo> lookup(ProductCycle productCycle) {
        String key = productCycle.product() + "/" + productCycle.cycle();
        return Optional.ofNullable(entries.get(key));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, EolInfo> load(ResourceLoader resourceLoader) {
        Resource resource = resourceLoader.getResource("classpath:eol-fallback.yml");
        if (!resource.exists()) {
            LOG.warn("eol-fallback.yml not found on the classpath — fallback store will be empty");
            return Map.of();
        }
        try (InputStream is = resource.getInputStream()) {
            Yaml yaml = new Yaml();
            Map<String, Object> config = yaml.load(is);
            Object rawCycles = config.get("cycles");
            if (!(rawCycles instanceof List<?> cycleList)) {
                LOG.warn("eol-fallback.yml has no 'cycles' list — fallback store will be empty");
                return Map.of();
            }
            Map<String, EolInfo> result = new HashMap<>();
            Instant loadedAt = Instant.now();
            for (Object item : cycleList) {
                if (!(item instanceof Map<?, ?> map)) continue;
                String product = (String) map.get("product");
                String cycle = (String) map.get("cycle");
                Object rawEol = map.get("eol");
                if (product == null || cycle == null) continue;

                LocalDate eolDate = rawEol instanceof String s ? LocalDate.parse(s) : null;
                EolInfo info = EolInfo.from(
                        new EolInfo.EolApiResponse(cycle, null, eolDate, null, null, null, null, null, null, null),
                        loadedAt);
                result.put(product + "/" + cycle, info);
            }
            LOG.info("Loaded {} EOL fallback entries from eol-fallback.yml", result.size());
            return Map.copyOf(result);
        } catch (Exception ex) {
            LOG.error("Failed to load eol-fallback.yml — fallback store will be empty", ex);
            return Map.of();
        }
    }
}
