package io.depguard;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
class ApplicationSmokeTest extends BaseIT {

    @Test
    void contextLoads() {
        // Verifies the full application context starts without errors
    }

    @Test
    void healthEndpointServesResponse() {
        restTestClient.get().uri("/api/health").exchange().expectStatus().isOk();
    }
}
