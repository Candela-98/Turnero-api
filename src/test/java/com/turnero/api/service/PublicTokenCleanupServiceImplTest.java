package com.turnero.api.service;

import com.turnero.api.config.PublicTokenCleanupProperties;
import com.turnero.api.model.Business;
import com.turnero.api.repository.AppointmentPublicTokenRepository;
import com.turnero.api.repository.BusinessRepository;
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
import org.springframework.data.domain.Sort;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PublicTokenCleanupServiceImplTest {

    private static final Instant REFERENCE = Instant.parse("2026-10-19T01:30:00Z");
    private static final LocalDateTime UTC_CUTOFF = LocalDateTime.of(2026, 9, 19, 1, 30);
    private static final Sort BUSINESS_ORDER = Sort.by(Sort.Direction.ASC, "id");

    @Mock private AppointmentPublicTokenRepository tokenRepository;
    @Mock private BusinessRepository businessRepository;
    @Mock private Clock clock;

    private PublicTokenCleanupProperties properties;
    private PublicTokenCleanupServiceImpl service;

    @BeforeEach
    void setUp() {
        properties = new PublicTokenCleanupProperties();
        properties.setRetentionDays(30);
        properties.setBatchSize(500);
        given(clock.instant()).willReturn(REFERENCE);
        service = new PublicTokenCleanupServiceImpl(tokenRepository, businessRepository, properties, clock);
    }

    @ParameterizedTest
    @CsvSource({
            "America/Argentina/Buenos_Aires, 2026-09-18T22:30:00",
            "Asia/Tokyo, 2026-09-19T10:30:00",
            "Europe/Berlin, 2026-09-19T03:30:00"
    })
    void cleanupPublicTokensBatch_usesBusinessLocalCutoffForSelectionAndDeletion(String timezone, String expected) {
        Business business = business(10L, timezone);
        LocalDateTime cutoff = LocalDateTime.parse(expected);
        List<Long> ids = List.of(100L, 101L);
        given(businessRepository.findAll(BUSINESS_ORDER)).willReturn(List.of(business));
        given(tokenRepository.findCleanupCandidateIds(10L, cutoff, PageRequest.of(0, 500))).willReturn(ids);
        given(tokenRepository.deleteCleanupCandidatesByIds(10L, ids, cutoff)).willReturn(2);

        assertThat(service.cleanupPublicTokensBatch()).isEqualTo(2);

        ArgumentCaptor<LocalDateTime> selectedCutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> deletedCutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        InOrder order = inOrder(tokenRepository);
        order.verify(tokenRepository).findCleanupCandidateIds(eq(10L), selectedCutoff.capture(), eq(PageRequest.of(0, 500)));
        order.verify(tokenRepository).deleteCleanupCandidatesByIds(eq(10L), same(ids), deletedCutoff.capture());
        order.verifyNoMoreInteractions();
        assertThat(selectedCutoff.getValue()).isEqualTo(cutoff);
        assertThat(deletedCutoff.getValue()).isSameAs(selectedCutoff.getValue());
        verify(clock).instant();
        verifyNoMoreInteractions(clock);
    }

    @Test
    void cleanupPublicTokensBatch_usesOneReferenceInstantAndProcessesBusinessesInRequestedIdOrder() {
        given(clock.instant()).willReturn(REFERENCE, REFERENCE.plusSeconds(3600));
        given(businessRepository.findAll(BUSINESS_ORDER)).willReturn(List.of(
                business(10L, "America/Argentina/Buenos_Aires"), business(20L, "Asia/Tokyo")));
        LocalDateTime westCutoff = LocalDateTime.of(2026, 9, 18, 22, 30);
        LocalDateTime eastCutoff = LocalDateTime.of(2026, 9, 19, 10, 30);
        given(tokenRepository.findCleanupCandidateIds(10L, westCutoff, PageRequest.of(0, 500))).willReturn(List.of());
        given(tokenRepository.findCleanupCandidateIds(20L, eastCutoff, PageRequest.of(0, 500))).willReturn(List.of());

        assertThat(service.cleanupPublicTokensBatch()).isZero();

        InOrder order = inOrder(businessRepository, tokenRepository);
        order.verify(businessRepository).findAll(BUSINESS_ORDER);
        order.verify(tokenRepository).findCleanupCandidateIds(10L, westCutoff, PageRequest.of(0, 500));
        order.verify(tokenRepository).findCleanupCandidateIds(20L, eastCutoff, PageRequest.of(0, 500));
        order.verifyNoMoreInteractions();
        verify(clock).instant();
        verifyNoMoreInteractions(clock);
    }

    @ParameterizedTest
    @CsvSource({"45, 123", "1, 1"})
    void cleanupPublicTokensBatch_usesConfiguredRetentionAndBatchSize(long retentionDays, int batchSize) {
        properties.setRetentionDays(retentionDays);
        properties.setBatchSize(batchSize);
        LocalDateTime cutoff = LocalDateTime.of(2026, 10, 19, 1, 30).minusDays(retentionDays);
        given(businessRepository.findAll(BUSINESS_ORDER)).willReturn(List.of(business(10L, "UTC")));
        given(tokenRepository.findCleanupCandidateIds(10L, cutoff, PageRequest.of(0, batchSize))).willReturn(List.of());

        assertThat(service.cleanupPublicTokensBatch()).isZero();

        verify(tokenRepository).findCleanupCandidateIds(10L, cutoff, PageRequest.of(0, batchSize));
        verifyNoMoreInteractions(tokenRepository);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void cleanupPublicTokensBatch_sharesBudgetBySelectedCandidatesAndReturnsActualDeletedTotal(int firstDeleted) {
        properties.setBatchSize(3);
        given(businessRepository.findAll(BUSINESS_ORDER)).willReturn(List.of(
                business(10L, "UTC"), business(20L, "UTC"), business(30L, "UTC")));
        List<Long> firstIds = List.of(100L, 101L);
        List<Long> secondIds = List.of(200L);
        given(tokenRepository.findCleanupCandidateIds(10L, UTC_CUTOFF, PageRequest.of(0, 3))).willReturn(firstIds);
        given(tokenRepository.deleteCleanupCandidatesByIds(10L, firstIds, UTC_CUTOFF)).willReturn(firstDeleted);
        given(tokenRepository.findCleanupCandidateIds(20L, UTC_CUTOFF, PageRequest.of(0, 1))).willReturn(secondIds);
        given(tokenRepository.deleteCleanupCandidatesByIds(20L, secondIds, UTC_CUTOFF)).willReturn(1);

        assertThat(service.cleanupPublicTokensBatch()).isEqualTo(firstDeleted + 1);

        InOrder order = inOrder(tokenRepository);
        order.verify(tokenRepository).findCleanupCandidateIds(10L, UTC_CUTOFF, PageRequest.of(0, 3));
        order.verify(tokenRepository).deleteCleanupCandidatesByIds(10L, firstIds, UTC_CUTOFF);
        order.verify(tokenRepository).findCleanupCandidateIds(20L, UTC_CUTOFF, PageRequest.of(0, 1));
        order.verify(tokenRepository).deleteCleanupCandidatesByIds(20L, secondIds, UTC_CUTOFF);
        order.verifyNoMoreInteractions();
    }

    @Test
    void cleanupPublicTokensBatch_stopsWhenFirstBusinessExhaustsBudgetEvenIfNothingWasDeleted() {
        properties.setBatchSize(2);
        List<Long> ids = List.of(100L, 101L);
        given(businessRepository.findAll(BUSINESS_ORDER)).willReturn(List.of(business(10L, "UTC"), business(20L, "UTC")));
        given(tokenRepository.findCleanupCandidateIds(10L, UTC_CUTOFF, PageRequest.of(0, 2))).willReturn(ids);
        given(tokenRepository.deleteCleanupCandidatesByIds(10L, ids, UTC_CUTOFF)).willReturn(0);

        assertThat(service.cleanupPublicTokensBatch()).isZero();

        verify(tokenRepository).findCleanupCandidateIds(10L, UTC_CUTOFF, PageRequest.of(0, 2));
        verify(tokenRepository).deleteCleanupCandidatesByIds(10L, ids, UTC_CUTOFF);
        verifyNoMoreInteractions(tokenRepository);
    }

    @Test
    void cleanupPublicTokensBatch_continuesAfterEmptyBusinessWithoutReducingBudget() {
        List<Long> ids = List.of(200L);
        given(businessRepository.findAll(BUSINESS_ORDER)).willReturn(List.of(business(10L, "UTC"), business(20L, "UTC")));
        given(tokenRepository.findCleanupCandidateIds(10L, UTC_CUTOFF, PageRequest.of(0, 500))).willReturn(List.of());
        given(tokenRepository.findCleanupCandidateIds(20L, UTC_CUTOFF, PageRequest.of(0, 500))).willReturn(ids);
        given(tokenRepository.deleteCleanupCandidatesByIds(20L, ids, UTC_CUTOFF)).willReturn(1);

        assertThat(service.cleanupPublicTokensBatch()).isEqualTo(1);

        InOrder order = inOrder(tokenRepository);
        order.verify(tokenRepository).findCleanupCandidateIds(10L, UTC_CUTOFF, PageRequest.of(0, 500));
        order.verify(tokenRepository).findCleanupCandidateIds(20L, UTC_CUTOFF, PageRequest.of(0, 500));
        order.verify(tokenRepository).deleteCleanupCandidatesByIds(20L, ids, UTC_CUTOFF);
        order.verifyNoMoreInteractions();
    }

    @Test
    void cleanupPublicTokensBatch_whenNoBusinesses_returnsZeroWithoutQueryingTokens() {
        given(businessRepository.findAll(BUSINESS_ORDER)).willReturn(List.of());

        assertThat(service.cleanupPublicTokensBatch()).isZero();

        verify(businessRepository).findAll(BUSINESS_ORDER);
        verifyNoMoreInteractions(businessRepository);
        verifyNoInteractions(tokenRepository);
    }

    @Test
    void cleanupPublicTokensBatch_whenAllBusinessesHaveNoCandidates_returnsZeroWithoutDeleting() {
        given(businessRepository.findAll(BUSINESS_ORDER)).willReturn(List.of(business(10L, "UTC"), business(20L, "UTC")));
        given(tokenRepository.findCleanupCandidateIds(10L, UTC_CUTOFF, PageRequest.of(0, 500))).willReturn(List.of());
        given(tokenRepository.findCleanupCandidateIds(20L, UTC_CUTOFF, PageRequest.of(0, 500))).willReturn(List.of());

        assertThat(service.cleanupPublicTokensBatch()).isZero();

        verify(tokenRepository).findCleanupCandidateIds(10L, UTC_CUTOFF, PageRequest.of(0, 500));
        verify(tokenRepository).findCleanupCandidateIds(20L, UTC_CUTOFF, PageRequest.of(0, 500));
        verifyNoMoreInteractions(tokenRepository);
    }

    @Test
    void cleanupPublicTokensBatch_repeatedExecutionsResetBudgetAndReferenceAndContinueRemainingBusinesses() {
        properties.setBatchSize(2);
        given(clock.instant()).willReturn(REFERENCE, REFERENCE.plusSeconds(86400));
        given(businessRepository.findAll(BUSINESS_ORDER)).willReturn(List.of(business(10L, "UTC"), business(20L, "UTC")));
        List<Long> firstIds = List.of(100L, 101L);
        List<Long> secondIds = List.of(200L, 201L);
        LocalDateTime nextCutoff = UTC_CUTOFF.plusDays(1);
        given(tokenRepository.findCleanupCandidateIds(10L, UTC_CUTOFF, PageRequest.of(0, 2))).willReturn(firstIds);
        given(tokenRepository.deleteCleanupCandidatesByIds(10L, firstIds, UTC_CUTOFF)).willReturn(2);
        given(tokenRepository.findCleanupCandidateIds(10L, nextCutoff, PageRequest.of(0, 2))).willReturn(List.of());
        given(tokenRepository.findCleanupCandidateIds(20L, nextCutoff, PageRequest.of(0, 2))).willReturn(secondIds);
        given(tokenRepository.deleteCleanupCandidatesByIds(20L, secondIds, nextCutoff)).willReturn(1);

        assertThat(service.cleanupPublicTokensBatch()).isEqualTo(2);
        assertThat(service.cleanupPublicTokensBatch()).isEqualTo(1);

        InOrder order = inOrder(businessRepository, tokenRepository);
        order.verify(businessRepository).findAll(BUSINESS_ORDER);
        order.verify(tokenRepository).findCleanupCandidateIds(10L, UTC_CUTOFF, PageRequest.of(0, 2));
        order.verify(tokenRepository).deleteCleanupCandidatesByIds(10L, firstIds, UTC_CUTOFF);
        order.verify(businessRepository).findAll(BUSINESS_ORDER);
        order.verify(tokenRepository).findCleanupCandidateIds(10L, nextCutoff, PageRequest.of(0, 2));
        order.verify(tokenRepository).findCleanupCandidateIds(20L, nextCutoff, PageRequest.of(0, 2));
        order.verify(tokenRepository).deleteCleanupCandidatesByIds(20L, secondIds, nextCutoff);
        order.verifyNoMoreInteractions();
        verify(clock, times(2)).instant();
        verifyNoMoreInteractions(clock);
    }

    @Test
    void cleanupPublicTokensBatch_repeatedExecutionAfterCandidatesDisappearReturnsZeroWithoutDeletingAgain() {
        List<Long> ids = List.of(100L);
        given(businessRepository.findAll(BUSINESS_ORDER)).willReturn(List.of(business(10L, "UTC")));
        given(tokenRepository.findCleanupCandidateIds(10L, UTC_CUTOFF, PageRequest.of(0, 500)))
                .willReturn(ids, List.of());
        given(tokenRepository.deleteCleanupCandidatesByIds(10L, ids, UTC_CUTOFF)).willReturn(1);

        assertThat(service.cleanupPublicTokensBatch()).isEqualTo(1);
        assertThat(service.cleanupPublicTokensBatch()).isZero();

        verify(tokenRepository, times(2)).findCleanupCandidateIds(10L, UTC_CUTOFF, PageRequest.of(0, 500));
        verify(tokenRepository).deleteCleanupCandidatesByIds(10L, ids, UTC_CUTOFF);
        verifyNoMoreInteractions(tokenRepository);
        verify(clock, times(2)).instant();
    }

    @Test
    void cleanupPublicTokensBatch_whenDeleteFails_propagatesExceptionWithoutProcessingNextBusiness() {
        List<Long> ids = List.of(100L);
        RuntimeException failure = new RuntimeException("Delete failed");
        given(businessRepository.findAll(BUSINESS_ORDER)).willReturn(List.of(business(10L, "UTC"), business(20L, "UTC")));
        given(tokenRepository.findCleanupCandidateIds(10L, UTC_CUTOFF, PageRequest.of(0, 500))).willReturn(ids);
        given(tokenRepository.deleteCleanupCandidatesByIds(10L, ids, UTC_CUTOFF)).willThrow(failure);

        assertThatThrownBy(service::cleanupPublicTokensBatch).isSameAs(failure);

        verify(tokenRepository).findCleanupCandidateIds(10L, UTC_CUTOFF, PageRequest.of(0, 500));
        verify(tokenRepository).deleteCleanupCandidatesByIds(10L, ids, UTC_CUTOFF);
        verifyNoMoreInteractions(tokenRepository);
    }

    private Business business(Long id, String timezone) {
        return Business.builder().id(id).timezone(timezone).build();
    }
}
