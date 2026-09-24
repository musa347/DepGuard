package io.depguard.remediation;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
class MavenCentralWebClientConfig {

    @Bean
    WebClient mavenCentralWebClient() {
        return WebClient.builder().baseUrl("https://search.maven.org").build();
    }
}
