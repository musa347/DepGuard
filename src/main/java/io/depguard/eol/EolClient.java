package io.depguard.eol;

import io.depguard.shared.AssertUtil;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Client for the endoflife.date REST API ({@code GET /api/{product}/{cycle}.json}).
 *
 * <p>5-second timeout, one retry after 500 ms on 5xx. Returns {@link Optional#empty} on 404 (product
 * not tracked by endoflife.date) — the caller treats that as {@code UNKNOWN}.
 */
@Component
public class EolClient {

    private static final Logger LOG = LoggerFactory.getLogger(EolClient.class);
    private static final int TIMEOUT_MS = 5_000;
    private static final int RETRY_DELAY_MS = 500;

    private final WebClient webClient;

    public EolClient(WebClient eolWebClient) {
        this.webClient = AssertUtil.requireNotNull(eolWebClient, "webClient must not be null");
    }

    /**
     * Fetches EOL information for a product/cycle.
     *
     * @return the EOL info, or empty if the product/cycle is not tracked (404) or the call failed
     *         after retry
     */
    public Optional<EolInfo> fetch(ProductCycle productCycle) {
        AssertUtil.requireNotNull(productCycle, "productCycle must not be null");
        Instant fetchedAt = Instant.now();
        return fetchWithRetry(productCycle, fetchedAt);
    }

    private Optional<EolInfo> fetchWithRetry(ProductCycle productCycle, Instant fetchedAt) {
        try {
            return doFetch(productCycle, fetchedAt);
        } catch (Exception ex) {
            if (isRetryable(ex)) {
                LOG.warn(
                        "endoflife.date call failed for {}/{} — retrying in {} ms: {}",
                        productCycle.product(),
                        productCycle.cycle(),
                        RETRY_DELAY_MS,
                        ex.getMessage());
                try {
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return Optional.empty();
                }
                try {
                    return doFetch(productCycle, fetchedAt);
                } catch (Exception retryEx) {
                    LOG.error(
                            "endoflife.date retry call failed for {}/{}: {}",
                            productCycle.product(),
                            productCycle.cycle(),
                            retryEx.getMessage());
                    return Optional.empty();
                }
            }
            LOG.error(
                    "endoflife.date call failed for {}/{} and is not retryable: {}",
                    productCycle.product(),
                    productCycle.cycle(),
                    ex.getMessage());
            return Optional.empty();
        }
    }

    private Optional<EolInfo> doFetch(ProductCycle productCycle, Instant fetchedAt) {
        EolInfo.EolApiResponse response;
        try {
            response = webClient
                    .get()
                    .uri("/api/{product}/{cycle}.json", productCycle.product(), productCycle.cycle())
                    .retrieve()
                    .bodyToMono(EolInfo.EolApiResponse.class)
                    .timeout(Duration.ofMillis(TIMEOUT_MS))
                    .block();
        } catch (WebClientResponseException.NotFound ex) {
            LOG.info("endoflife.date: {}/{} not tracked (404)", productCycle.product(), productCycle.cycle());
            return Optional.empty();
        } catch (WebClientResponseException ex) {
            throw ex; // retryable if 5xx
        }
        return Optional.of(EolInfo.from(response, fetchedAt));
    }

    private boolean isRetryable(Exception ex) {
        if (ex instanceof WebClientResponseException wce) {
            return wce.getStatusCode().is5xxServerError();
        }
        // network/IO errors are retryable
        return ex instanceof RuntimeException && !(ex instanceof IllegalArgumentException);
    }
}
