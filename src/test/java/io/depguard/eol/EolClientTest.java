package io.depguard.eol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.Optional;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Unit tests for {@link EolClient} using MockWebServer to simulate endoflife.date responses.
 */
class EolClientTest {

    private MockWebServer server;
    private EolClient eolClient;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        WebClient webClient =
                WebClient.builder().baseUrl(server.url("/").toString()).build();
        eolClient = new EolClient(webClient);
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void fetchesEolInfoForAValidProductAndCycle() throws Exception {
        server.enqueue(
                new MockResponse().setHeader("Content-Type", "application/json").setBody("""
                        {
                          "cycle": "2.7",
                          "release": "2019-09-30",
                          "eol": "2021-11-30",
                          "latest": "2.7.18",
                          "latestRelease": "2021-11-30",
                          "support": null,
                          "docs": "https://endoflife.date/spring-boot",
                          "blog": "https://spring.io/blog",
                          "discuss": "https://stackoverflow.com/questions/tagged/spring-boot",
                          "stackoverflow": null
                        }
                        """));

        Optional<EolInfo> result = eolClient.fetch(new ProductCycle("spring-boot", "2.7"));

        assertThat(result).isPresent();
        EolInfo info = result.get();
        assertThat(info.status()).isEqualTo(EolStatus.EOL);
        assertThat(info.isEol()).isTrue();
        assertThat(info.eolDate()).isEqualTo(LocalDate.of(2021, 11, 30));
        assertThat(info.dataSourceFetchedAt()).isNotNull();
        assertThat(server.takeRequest().getPath()).isEqualTo("/api/spring-boot/2.7.json");
    }

    @Test
    void returnsEmptyForAnUnknownProduct() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(404)
                .setHeader("Content-Type", "application/json")
                .setBody("{}"));

        Optional<EolInfo> result = eolClient.fetch(new ProductCycle("nonexistent-product", "1.0"));

        assertThat(result).isEmpty();
    }

    @Test
    void retriesOnceOn5xxAndSucceeds() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(500)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"error\":\"server error\"}"));
        server.enqueue(
                new MockResponse().setHeader("Content-Type", "application/json").setBody("""
                        {
                          "cycle": "3.0",
                          "release": "2022-05-13",
                          "eol": null,
                          "latest": "3.0.14",
                          "latestRelease": "2023-11-16",
                          "support": "maintenance",
                          "docs": "https://endoflife.date/spring-boot",
                          "blog": "https://spring.io/blog",
                          "discuss": null,
                          "stackoverflow": null
                        }
                        """));

        Optional<EolInfo> result = eolClient.fetch(new ProductCycle("spring-boot", "3.0"));

        assertThat(result).isPresent();
        assertThat(result.get().status()).isEqualTo(EolStatus.MAINTENANCE);
        // Two requests: initial + retry
        assertThat(server.getRequestCount()).isEqualTo(2);
    }

    @Test
    void returnsEmptyAfterRetryFailsOnPersistent5xx() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(500)
                .setHeader("Content-Type", "application/json")
                .setBody("{}"));
        server.enqueue(new MockResponse()
                .setResponseCode(503)
                .setHeader("Content-Type", "application/json")
                .setBody("{}"));

        Optional<EolInfo> result = eolClient.fetch(new ProductCycle("log4j", "2.17"));

        assertThat(result).isEmpty();
        assertThat(server.getRequestCount()).isEqualTo(2);
    }

    @Test
    void returnsEmptyOnConnectionError() throws Exception {
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));

        Optional<EolInfo> result = eolClient.fetch(new ProductCycle("spring-framework", "5.3"));

        assertThat(result).isEmpty();
        assertThat(server.getRequestCount()).isEqualTo(2);
    }

    @Test
    void rejectsNullProductCycle() {
        assertThatThrownBy(() -> eolClient.fetch(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("productCycle must not be null");
    }
}
