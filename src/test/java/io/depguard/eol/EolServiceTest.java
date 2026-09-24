package io.depguard.eol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.depguard.dependency.ScanDependencyCoords;
import io.depguard.dependency.ScanDependencyRepository;
import io.depguard.scan.ScanRepository;
import io.depguard.shared.ScanId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EolServiceTest {

    private static final ScanId SCAN_ID = ScanId.of("0RZKMN9SVD510");
    private static final String DEPENDENCY_ID = "0RZKMN9SVD511";
    private static final String GROUP_ID = "org.springframework.boot";
    private static final String ARTIFACT_ID = "spring-boot-starter";
    private static final String VERSION = "2.7.18";

    @Mock
    ScanRepository scanRepository;

    @Mock
    ScanDependencyRepository scanDependencyRepository;

    @Mock
    EolMappingStrategy mappingStrategy;

    @Mock
    EolClient eolClient;

    @Mock
    EolFallbackStore fallbackStore;

    @Mock
    EolRepository eolRepository;

    @InjectMocks
    EolService eolService;

    @Test
    void doesNothingWhenScanHasNoDependencies() {
        given(scanDependencyRepository.findScanDependenciesWithCoords(SCAN_ID.id()))
                .willReturn(List.of());

        eolService.enrichScan(SCAN_ID);

        verify(scanDependencyRepository).findScanDependenciesWithCoords(SCAN_ID.id());
        verifyNoInteractions(mappingStrategy, eolClient, eolRepository);
    }

    @Test
    void recordsUnknownWhenNoMappingExists() {
        ScanDependencyCoords coords = coords();
        given(scanDependencyRepository.findScanDependenciesWithCoords(SCAN_ID.id()))
                .willReturn(List.of(coords));
        given(eolRepository.findByScanAndDependencyId(SCAN_ID.id(), DEPENDENCY_ID))
                .willReturn(null);
        given(mappingStrategy.resolve(GROUP_ID, ARTIFACT_ID, VERSION)).willReturn(Optional.empty());

        eolService.enrichScan(SCAN_ID);

        ArgumentCaptor<List<EolRecord>> captor = ArgumentCaptor.forClass(List.class);
        verify(eolRepository).saveAll(captor.capture());
        EolRecord record = captor.getValue().get(0);
        assertThat(record.getId().dependencyId()).isEqualTo(DEPENDENCY_ID);
        assertThat(record.getId().scanId()).isEqualTo(SCAN_ID.id());
        assertThat(record.getStatus()).isEqualTo(EolStatus.UNKNOWN);
        assertThat(record.getSource()).isEqualTo("NO_MAPPING");
        assertThat(record.getEolDate()).isNull();
        verify(mappingStrategy).resolve(GROUP_ID, ARTIFACT_ID, VERSION);
        verifyNoInteractions(eolClient);
    }

    @Test
    void recordsUnknownWhenApiReturnsNoData() {
        ScanDependencyCoords coords = coords();
        ProductCycle pc = new ProductCycle("spring-boot", "2.7");
        given(scanDependencyRepository.findScanDependenciesWithCoords(SCAN_ID.id()))
                .willReturn(List.of(coords));
        given(eolRepository.findByScanAndDependencyId(SCAN_ID.id(), DEPENDENCY_ID))
                .willReturn(null);
        given(mappingStrategy.resolve(GROUP_ID, ARTIFACT_ID, VERSION)).willReturn(Optional.of(pc));
        given(eolClient.fetch(pc)).willReturn(Optional.empty());
        given(fallbackStore.lookup(pc)).willReturn(Optional.empty()); // no fallback data either

        eolService.enrichScan(SCAN_ID);

        ArgumentCaptor<List<EolRecord>> captor = ArgumentCaptor.forClass(List.class);
        verify(eolRepository).saveAll(captor.capture());
        EolRecord record = captor.getValue().get(0);
        assertThat(record.getStatus()).isEqualTo(EolStatus.UNKNOWN);
        assertThat(record.getSource()).isEqualTo("API");
        assertThat(record.getEolDate()).isNull();
        verify(eolClient).fetch(pc);
        verify(fallbackStore).lookup(pc);
    }

    @Test
    void recordsFallbackDataWhenApiFailsButFallbackHasEntry() {
        ScanDependencyCoords coords = coords();
        ProductCycle pc = new ProductCycle("spring-boot", "2.7");
        given(scanDependencyRepository.findScanDependenciesWithCoords(SCAN_ID.id()))
                .willReturn(List.of(coords));
        given(eolRepository.findByScanAndDependencyId(SCAN_ID.id(), DEPENDENCY_ID))
                .willReturn(null);
        given(mappingStrategy.resolve(GROUP_ID, ARTIFACT_ID, VERSION)).willReturn(Optional.of(pc));
        given(eolClient.fetch(pc)).willReturn(Optional.empty()); // API down
        EolInfo fallbackInfo = new EolInfo(LocalDate.of(2023, 11, 24), true, EolStatus.EOL, Instant.now());
        given(fallbackStore.lookup(pc)).willReturn(Optional.of(fallbackInfo));

        eolService.enrichScan(SCAN_ID);

        ArgumentCaptor<List<EolRecord>> captor = ArgumentCaptor.forClass(List.class);
        verify(eolRepository).saveAll(captor.capture());
        EolRecord record = captor.getValue().get(0);
        assertThat(record.getStatus()).isEqualTo(EolStatus.EOL);
        assertThat(record.getSource()).isEqualTo("FALLBACK");
        assertThat(record.getEolDate()).isEqualTo(LocalDate.of(2023, 11, 24));
    }

    @Test
    void recordsEolDataWhenApiReturnsValidResponse() {
        ScanDependencyCoords coords = coords();
        ProductCycle pc = new ProductCycle("spring-boot", "2.7");
        given(scanDependencyRepository.findScanDependenciesWithCoords(SCAN_ID.id()))
                .willReturn(List.of(coords));
        given(eolRepository.findByScanAndDependencyId(SCAN_ID.id(), DEPENDENCY_ID))
                .willReturn(null);
        given(mappingStrategy.resolve(GROUP_ID, ARTIFACT_ID, VERSION)).willReturn(Optional.of(pc));
        given(eolClient.fetch(pc))
                .willReturn(Optional.of(new EolInfo(LocalDate.of(2021, 11, 30), true, EolStatus.EOL, Instant.now())));

        eolService.enrichScan(SCAN_ID);

        ArgumentCaptor<List<EolRecord>> captor = ArgumentCaptor.forClass(List.class);
        verify(eolRepository).saveAll(captor.capture());
        EolRecord record = captor.getValue().get(0);
        assertThat(record.getStatus()).isEqualTo(EolStatus.EOL);
        assertThat(record.getEolDate()).isEqualTo(LocalDate.of(2021, 11, 30));
        assertThat(record.getSource()).isEqualTo("API");
        assertThat(record.getDataSourceFetchedAt()).isNotNull();
    }

    private static ScanDependencyCoords coords() {
        return new ScanDependencyCoords(DEPENDENCY_ID, SCAN_ID.id(), GROUP_ID, ARTIFACT_ID, VERSION, "compile", true);
    }
}
