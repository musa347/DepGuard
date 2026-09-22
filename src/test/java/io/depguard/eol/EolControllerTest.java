package io.depguard.eol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import io.depguard.scan.ScanRepository;
import io.depguard.shared.ScanId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(EolController.class)
class EolControllerTest {

    private static final String SCAN_ID = "0RZKMN9SVD510";

    @Autowired
    MockMvcTester mockMvc;

    @MockitoBean
    EolService eolService;

    @MockitoBean
    ScanRepository scanRepository;

    @MockitoBean
    EolRepository eolRepository;

    @Test
    void triggersEolCheckAndReturnsAccepted() {
        given(scanRepository.existsById(any(ScanId.class))).willReturn(true);
        ScanId id = ScanId.of(SCAN_ID);

        assertThat(mockMvc.post().uri("/api/scans/{id}/eol-check", SCAN_ID))
                .hasStatus(HttpStatus.ACCEPTED)
                .bodyJson()
                .extractingPath("$.scanId")
                .isEqualTo(SCAN_ID);

        verify(eolService).enrichScan(id);
    }

    @Test
    void returnsNotFoundWhenScanDoesNotExist() {
        given(scanRepository.existsById(any(ScanId.class))).willReturn(false);

        assertThat(mockMvc.post().uri("/api/scans/{id}/eol-check", SCAN_ID))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.error")
                .isEqualTo("NOT_FOUND");
    }

    @Test
    void returnsEolReportWhenEnrichmentHasRun() {
        given(scanRepository.existsById(any(ScanId.class))).willReturn(true);
        ScanId id = ScanId.of(SCAN_ID);
        EolDependencyEntry entry = new EolDependencyEntry(
                "org.springframework.boot",
                "spring-boot-starter",
                "2.7.18",
                EolStatus.EOL,
                LocalDate.of(2021, 11, 30),
                "API",
                Instant.now());
        given(eolRepository.findEolReportByScanId(SCAN_ID)).willReturn(List.of(entry));

        assertThat(mockMvc.get().uri("/api/scans/{id}/eol", SCAN_ID))
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .extractingPath("$.scanId")
                .isEqualTo(SCAN_ID);

        assertThat(mockMvc.get().uri("/api/scans/{id}/eol", SCAN_ID))
                .bodyJson()
                .extractingPath("$.dependencies")
                .asList()
                .hasSize(1);
    }

    @Test
    void returnsEmptyListWhenNoEnrichmentHasRun() {
        given(scanRepository.existsById(any(ScanId.class))).willReturn(true);

        assertThat(mockMvc.get().uri("/api/scans/{id}/eol", SCAN_ID))
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .extractingPath("$.dependencies")
                .asList()
                .isEmpty();
    }

    @Test
    void returnsNotFoundWhenScanDoesNotExistOnGet() {
        given(scanRepository.existsById(any(ScanId.class))).willReturn(false);

        assertThat(mockMvc.get().uri("/api/scans/{id}/eol", SCAN_ID))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.error")
                .isEqualTo("NOT_FOUND");
    }
}
