package io.depguard.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.depguard.shared.GitHubUrlValidator;
import io.depguard.shared.ResourceNotFoundException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    private static final String PAYMENT_SERVICE_URL = "https://github.com/depguard-it/payment-service";

    private static final String BILLING_SERVICE_URL = "https://github.com/depguard-it/billing-service";

    @Mock
    ProjectRepository projectRepository;

    @InjectMocks
    ProjectService projectService;

    @Test
    void registersProjectWithCanonicalRepositoryUrl() {
        given(projectRepository.existsByRepositoryUrl(PAYMENT_SERVICE_URL)).willReturn(false);
        given(projectRepository.save(any(Project.class))).willAnswer(invocation -> invocation.getArgument(0));

        ProjectResult result = projectService.createProject(
                new CreateProjectCmd("payment-service", PAYMENT_SERVICE_URL + ".git", null));

        ArgumentCaptor<Project> saved = ArgumentCaptor.forClass(Project.class);
        verify(projectRepository).save(saved.capture());
        assertThat(saved.getValue().getId()).isNotNull();
        assertThat(saved.getValue().getRepositoryUrl()).isEqualTo(PAYMENT_SERVICE_URL);
        assertThat(saved.getValue().getDefaultBranch()).isNull();

        assertThat(result.id()).isNotNull();
        assertThat(result.name()).isEqualTo("payment-service");
        assertThat(result.repositoryUrl()).isEqualTo(PAYMENT_SERVICE_URL);
        assertThat(result.defaultBranch()).isNull();
    }

    @Test
    void registersProjectWithRequestedDefaultBranch() {
        given(projectRepository.existsByRepositoryUrl(PAYMENT_SERVICE_URL)).willReturn(false);
        given(projectRepository.save(any(Project.class))).willAnswer(invocation -> invocation.getArgument(0));

        ProjectResult result =
                projectService.createProject(new CreateProjectCmd("payment-service", PAYMENT_SERVICE_URL, "trunk"));

        assertThat(result.defaultBranch()).isEqualTo("trunk");
    }

    @Test
    void rejectsRepositoryThatIsNotAPublicGitHubUrl() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> projectService.createProject(
                        new CreateProjectCmd("payment-service", "https://gitlab.com/user/payment-service", "main")))
                .withMessage(GitHubUrlValidator.REJECTION_MESSAGE);

        verifyNoInteractions(projectRepository);
    }

    @Test
    void rejectsRepositoryThatIsAlreadyRegistered() {
        given(projectRepository.existsByRepositoryUrl(PAYMENT_SERVICE_URL)).willReturn(true);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> projectService.createProject(
                        new CreateProjectCmd("duplicate", PAYMENT_SERVICE_URL + "/", "main")))
                .withMessageContaining("already registered");

        verify(projectRepository, never()).save(any(Project.class));
    }

    @Test
    void returnsProjectById() {
        Project project = paymentService();
        given(projectRepository.findById(project.getId())).willReturn(Optional.of(project));

        ProjectResult result = projectService.getProject(project.getId());

        assertThat(result.id()).isEqualTo(project.getId());
        assertThat(result.name()).isEqualTo("payment-service");
        assertThat(result.repositoryUrl()).isEqualTo(PAYMENT_SERVICE_URL);
        assertThat(result.defaultBranch()).isEqualTo("main");
    }

    @Test
    void failsWhenProjectDoesNotExist() {
        ProjectId unknown = ProjectId.of("0KX1Q2W3E4R5T");
        given(projectRepository.findById(unknown)).willReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.getProject(unknown))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Project not found: " + unknown);
    }

    @Test
    void listsProjectsNewestFirst() {
        Project billingService =
                new Project(ProjectId.of("0KX1Q2W3E4R5S"), "billing-service", BILLING_SERVICE_URL, "main");
        given(projectRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")))
                .willReturn(List.of(billingService, paymentService()));

        List<ProjectResult> results = projectService.listProjects();

        assertThat(results).extracting(ProjectResult::name).containsExactly("billing-service", "payment-service");
    }

    private static Project paymentService() {
        return new Project(ProjectId.of("0KX1Q2W3E4R5T"), "payment-service", PAYMENT_SERVICE_URL, "main");
    }
}
