package io.depguard.eol;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/** Configures the {@link WebClient} used by {@link EolClient} — 5-second timeout. */
@Configuration
class WebClientConfig {

    @Bean
    WebClient eolWebClient() {
        return WebClient.builder()
                .baseUrl("https://endoflife.date")
                .codecs(c -> c.defaultCodecs().maxInMemorySize(256 * 1024))
                .build();
    }
}
