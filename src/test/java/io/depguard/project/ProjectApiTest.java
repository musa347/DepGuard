package io.depguard.project;

import static org.assertj.core.api.Assertions.assertThat;

import io.depguard.BaseIT;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.ExchangeResult;

class ProjectApiTest extends BaseIT {

    @Test
    void registersRetrievesAndListsProject() {
        EntityExchangeResult<ProjectResponse> created = restTestClient
                .post()
                .uri("/api/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"name":"payment-service","repositoryUrl":"https://github.com/depguard-it/payment-service.git"}
                        """)
                .exchange()
                .expectStatus()
                .isCreated()
                .returnResult(ProjectResponse.class);

        ProjectResponse registered = created.getResponseBody();
        assertThat(registered).isNotNull();
        assertThat(registered.id()).isNotBlank();
        assertThat(registered.name()).isEqualTo("payment-service");
        // the ".git" suffix is normalised away before persisting
        assertThat(registered.repositoryUrl()).isEqualTo("https://github.com/depguard-it/payment-service");
        assertThat(registered.defaultBranch()).isEqualTo("main");
        assertThat(registered.createdAt()).isNotNull();
        assertThat(created.getResponseHeaders().getLocation()).isNotNull();
        assertThat(created.getResponseHeaders().getLocation().getPath()).isEqualTo("/api/projects/" + registered.id());

        ProjectResponse fetched = restTestClient
                .get()
                .uri("/api/projects/{id}", registered.id())
                .exchange()
                .expectStatus()
                .isOk()
                .returnResult(ProjectResponse.class)
                .getResponseBody();

        assertThat(fetched).isNotNull();
        assertThat(fetched.id()).isEqualTo(registered.id());
        assertThat(fetched.name()).isEqualTo(registered.name());
        assertThat(fetched.repositoryUrl()).isEqualTo(registered.repositoryUrl());
        assertThat(fetched.defaultBranch()).isEqualTo(registered.defaultBranch());

        List<ProjectResponse> projects = restTestClient
                .get()
                .uri("/api/projects")
                .exchange()
                .expectStatus()
                .isOk()
                .returnResult(new ParameterizedTypeReference<List<ProjectResponse>>() {})
                .getResponseBody();

        assertThat(projects).extracting(ProjectResponse::id).contains(registered.id());
    }

    @Test
    void rejectsRepositoryUrlThatIsNotGitHub() {
        ExchangeResult rejected = restTestClient
                .post()
                .uri("/api/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"name":"gitlab-service","repositoryUrl":"https://gitlab.com/depguard-it/gitlab-service"}
                        """)
                .exchange()
                .expectStatus()
                .isBadRequest()
                .returnResult();

        assertThat(bodyOf(rejected)).contains("VALIDATION_ERROR").contains("Only public GitHub URLs are accepted");
    }

    @Test
    void rejectsProjectThatIsAlreadyRegistered() {
        registerProject("billing-service", "https://github.com/depguard-it/billing-service");

        ExchangeResult duplicate = restTestClient
                .post()
                .uri("/api/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"name":"billing-service-again","repositoryUrl":"https://github.com/depguard-it/billing-service/"}
                        """)
                .exchange()
                .expectStatus()
                .isBadRequest()
                .returnResult();

        assertThat(bodyOf(duplicate)).contains("already registered");
    }

    @Test
    void failsForUnknownProject() {
        ExchangeResult notFound = restTestClient
                .get()
                .uri("/api/projects/0KX1Q2W3E4R5T")
                .exchange()
                .expectStatus()
                .isNotFound()
                .returnResult();

        assertThat(bodyOf(notFound)).contains("NOT_FOUND").contains("Project not found: 0KX1Q2W3E4R5T");
    }

    private void registerProject(String name, String repositoryUrl) {
        restTestClient
                .post()
                .uri("/api/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"name":"%s","repositoryUrl":"%s"}
                        """.formatted(name, repositoryUrl))
                .exchange()
                .expectStatus()
                .isCreated();
    }

    private static String bodyOf(ExchangeResult result) {
        return new String(result.getResponseBodyContent(), StandardCharsets.UTF_8);
    }
}
