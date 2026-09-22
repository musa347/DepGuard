package io.depguard.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import io.depguard.shared.GitHubUrlValidator;
import io.depguard.shared.ResourceNotFoundException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(ProjectController.class)
class ProjectControllerTest {

    private static final String PAYMENT_SERVICE_ID = "0KX1Q2W3E4R5T";

    private static final ProjectResult PAYMENT_SERVICE = new ProjectResult(
            ProjectId.of(PAYMENT_SERVICE_ID),
            "payment-service",
            "https://github.com/depguard-it/payment-service",
            "main",
            Instant.parse("2026-09-21T16:00:00Z"));

    private static final ProjectResult BILLING_SERVICE = new ProjectResult(
            ProjectId.of("0KX1Q2W3E4R5S"),
            "billing-service",
            "https://github.com/depguard-it/billing-service",
            "main",
            Instant.parse("2026-09-21T15:00:00Z"));

    @Autowired
    MockMvcTester mockMvc;

    @MockitoBean
    ProjectService projectService;

    @Test
    void registersProjectAndPointsAtItsLocation() {
        given(projectService.createProject(any(CreateProjectCmd.class))).willReturn(PAYMENT_SERVICE);

        assertThat(mockMvc.post()
                        .uri("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"payment-service","repositoryUrl":"https://github.com/depguard-it/payment-service"}
                                """))
                .hasStatus(HttpStatus.CREATED)
                .hasHeader("Location", "http://localhost/api/projects/" + PAYMENT_SERVICE_ID)
                .bodyJson()
                .extractingPath("$.id")
                .isEqualTo(PAYMENT_SERVICE_ID);
    }

    @Test
    void rejectsRepositoryUrlThatIsNotGitHub() {
        given(projectService.createProject(any(CreateProjectCmd.class)))
                .willThrow(new IllegalArgumentException(GitHubUrlValidator.REJECTION_MESSAGE));

        String requestBody = """
                {"name":"payment-service","repositoryUrl":"https://gitlab.com/depguard-it/payment-service"}
                """;

        assertThat(mockMvc.post()
                        .uri("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.error")
                .isEqualTo("VALIDATION_ERROR");

        assertThat(mockMvc.post()
                        .uri("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .bodyJson()
                .extractingPath("$.message")
                .isEqualTo(GitHubUrlValidator.REJECTION_MESSAGE);
    }

    @Test
    void rejectsRequestWithoutName() {
        assertThat(mockMvc.post()
                        .uri("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"repositoryUrl":"https://github.com/depguard-it/payment-service"}
                                """))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyText()
                .contains("Project name must not be blank");
    }

    @Test
    void rejectsMalformedRequestBody() {
        assertThat(mockMvc.post()
                        .uri("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.error")
                .isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void listsProjects() {
        given(projectService.listProjects()).willReturn(List.of(PAYMENT_SERVICE, BILLING_SERVICE));

        assertThat(mockMvc.get().uri("/api/projects"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$")
                .asArray()
                .hasSize(2);

        assertThat(mockMvc.get().uri("/api/projects"))
                .bodyJson()
                .extractingPath("$[0].name")
                .isEqualTo("payment-service");
    }

    @Test
    void returnsProject() {
        given(projectService.getProject(ProjectId.of(PAYMENT_SERVICE_ID))).willReturn(PAYMENT_SERVICE);

        assertThat(mockMvc.get().uri("/api/projects/{id}", PAYMENT_SERVICE_ID))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.repositoryUrl")
                .isEqualTo("https://github.com/depguard-it/payment-service");
    }

    @Test
    void failsWhenProjectDoesNotExist() {
        given(projectService.getProject(ProjectId.of(PAYMENT_SERVICE_ID)))
                .willThrow(new ResourceNotFoundException("Project not found: " + PAYMENT_SERVICE_ID));

        assertThat(mockMvc.get().uri("/api/projects/{id}", PAYMENT_SERVICE_ID))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.error")
                .isEqualTo("NOT_FOUND");

        // proves the path variable is converted to a ProjectId before reaching the service
        verify(projectService).getProject(ProjectId.of(PAYMENT_SERVICE_ID));
    }
}
