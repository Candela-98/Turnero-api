package com.turnero.api.service;

import com.turnero.api.config.SessionCleanupProperties;
import com.turnero.api.repository.UserSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionCleanupServiceImplTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 7, 12, 0);
    private static final LocalDateTime CUTOFF = NOW.minusDays(30);
    private static final List<Long> IDS = List.of(10L, 20L, 30L);

    @Mock
    private UserSessionRepository repository;

    private SessionCleanupProperties properties;
    private Clock clock;
    private SessionCleanupServiceImpl service;

    @BeforeEach
    void setUp() {
        properties = new SessionCleanupProperties();
        properties.setRetentionDays(30);
        properties.setBatchSize(500);
        clock = spy(Clock.fixed(Instant.parse("2026-10-07T15:00:00Z"),
                ZoneId.of("America/Argentina/Buenos_Aires")));
        service = new SessionCleanupServiceImpl(repository, properties, clock);
    }

    @Test
    void cleanupSessionsBatch_whenNoCandidates_returnsZeroWithoutDeleting() {
        given(repository.findCleanupCandidateIds(CUTOFF, PageRequest.of(0, 500))).willReturn(List.of());

        assertThat(service.cleanupSessionsBatch()).isZero();

        verify(repository).findCleanupCandidateIds(CUTOFF, PageRequest.of(0, 500));
        verifyNoMoreInteractions(repository);
        verify(clock).instant();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2, 3})
    void cleanupSessionsBatch_returnsActualDeletedCountAndReusesCutoff(int deletedCount) {
        given(repository.findCleanupCandidateIds(CUTOFF, PageRequest.of(0, 500))).willReturn(IDS);
        given(repository.deleteCleanupCandidatesByIds(IDS, CUTOFF)).willReturn(deletedCount);

        assertThat(service.cleanupSessionsBatch()).isEqualTo(deletedCount);

        ArgumentCaptor<LocalDateTime> selectedCutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> deletedCutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        InOrder order = inOrder(repository);
        order.verify(repository).findCleanupCandidateIds(selectedCutoff.capture(), eq(PageRequest.of(0, 500)));
        order.verify(repository).deleteCleanupCandidatesByIds(same(IDS), deletedCutoff.capture());
        order.verifyNoMoreInteractions();
        assertThat(selectedCutoff.getValue()).isEqualTo(CUTOFF);
        assertThat(deletedCutoff.getValue()).isSameAs(selectedCutoff.getValue());
        verify(clock).instant();
    }

    @ParameterizedTest
    @CsvSource({"30, 123", "45, 500"})
    void cleanupSessionsBatch_usesConfiguredRetentionAndFirstPage(long retentionDays, int batchSize) {
        properties.setRetentionDays(retentionDays);
        properties.setBatchSize(batchSize);
        LocalDateTime expectedCutoff = NOW.minusDays(retentionDays);
        given(repository.findCleanupCandidateIds(expectedCutoff, PageRequest.of(0, batchSize)))
                .willReturn(List.of());

        assertThat(service.cleanupSessionsBatch()).isZero();

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findCleanupCandidateIds(eq(expectedCutoff), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(batchSize);
        assertThat(pageable.getValue().getSort().isUnsorted()).isTrue();
        verifyNoMoreInteractions(repository);
        verify(clock).instant();
    }

    @Test
    void cleanupSessionsBatch_whenDeleteFails_propagatesException() {
        RuntimeException failure = new RuntimeException("Delete failed");
        given(repository.findCleanupCandidateIds(CUTOFF, PageRequest.of(0, 500))).willReturn(IDS);
        given(repository.deleteCleanupCandidatesByIds(IDS, CUTOFF)).willThrow(failure);

        assertThatThrownBy(service::cleanupSessionsBatch).isSameAs(failure);

        verify(repository).findCleanupCandidateIds(CUTOFF, PageRequest.of(0, 500));
        verify(repository).deleteCleanupCandidatesByIds(IDS, CUTOFF);
        verifyNoMoreInteractions(repository);
        verify(clock).instant();
    }
}
