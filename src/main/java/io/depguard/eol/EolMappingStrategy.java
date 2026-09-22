package io.depguard.eol;

import java.util.Optional;

/** Maps a Maven coordinate to a product/cycle that endoflife.date understands. */
public interface EolMappingStrategy {

    /**
     * Resolves the endoflife.date product name and version cycle for the given dependency.
     *
     * @return the product and cycle, or empty if the groupId/artifact is not recognised
     */
    Optional<ProductCycle> resolve(String groupId, String artifactId, String version);
}
