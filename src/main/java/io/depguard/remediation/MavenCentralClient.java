package io.depguard.remediation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.depguard.shared.AssertUtil;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/** Reads Maven artifact versions from Maven Central's Solr API without downloading artifacts. */
@Component
public class MavenCentralClient {

    private final WebClient webClient;

    public MavenCentralClient(WebClient mavenCentralWebClient) {
        this.webClient = AssertUtil.requireNotNull(mavenCentralWebClient, "mavenCentralWebClient must not be null");
    }

    /** Returns up to twenty versions, newest first according to Maven Central's timestamp ordering. */
    public List<String> findCandidateVersions(String groupId, String artifactId) {
        MavenCentralResponse response = webClient
                .get()
                .uri(builder -> builder.path("/solrsearch/select")
                        .queryParam("q", "g:\"" + groupId + "\" AND a:\"" + artifactId + "\"")
                        .queryParam("core", "gav")
                        .queryParam("rows", 20)
                        .queryParam("wt", "json")
                        .queryParam("sort", "timestamp desc")
                        .build())
                .retrieve()
                .bodyToMono(MavenCentralResponse.class)
                .block();
        if (response == null
                || response.response() == null
                || response.response().docs() == null) return List.of();
        return response.response().docs().stream()
                .map(MavenCentralDocument::v)
                .filter(version -> version != null && !version.isBlank())
                .toList();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record MavenCentralResponse(MavenCentralDocs response) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record MavenCentralDocs(List<MavenCentralDocument> docs) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record MavenCentralDocument(String v) {}
}
