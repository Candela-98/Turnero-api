package com.turnero.api.repository;

import com.turnero.api.model.UserSession;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class UserSessionRepositoryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 7, 12, 0);
    private static final LocalDateTime CUTOFF = NOW.minusDays(30);

    @Autowired
    private UserSessionRepository repository;

    @Autowired
    private EntityManager entityManager;

    @ParameterizedTest(name = "{0}")
    @MethodSource("eligibilityCases")
    void selectsOnlyEligibleIds(String description, LocalDateTime expiresAt, LocalDateTime revokedAt,
                               boolean eligible) {
        Long id = saveSession(expiresAt, revokedAt).getId();
        flushAndClear();

        List<Long> ids = repository.findCleanupCandidateIds(CUTOFF, PageRequest.of(0, 500));

        assertThat(ids).isEqualTo(eligible ? List.of(id) : List.of());
    }

    static Stream<Arguments> eligibilityCases() {
        return Stream.of(
                Arguments.of("active", NOW.plusDays(7), null, false),
                Arguments.of("recently expired", NOW.minusDays(5), null, false),
                Arguments.of("old expiration", CUTOFF.minusSeconds(1), null, true),
                Arguments.of("expiration exactly at cutoff", CUTOFF, null, false),
                Arguments.of("recent revocation protects old expiration", CUTOFF.minusDays(30),
                        NOW.minusDays(5), false),
                Arguments.of("old revocation", CUTOFF.minusDays(10), CUTOFF.minusSeconds(1), true),
                Arguments.of("revocation exactly at cutoff protects old expiration", CUTOFF.minusDays(10),
                        CUTOFF, false),
                Arguments.of("old revocation with future expiration", NOW.plusDays(7),
                        CUTOFF.minusSeconds(1), true)
        );
    }

    @Test
    void selectsIdsInAscendingOrderAndRespectsPageableLimit() {
        List<Long> candidateIds = new ArrayList<>();
        saveSession(NOW.plusDays(1), null);
        for (int index = 0; index < 7; index++) {
            candidateIds.add(saveSession(CUTOFF.minusDays(index + 1), null).getId());
        }
        flushAndClear();

        List<Long> ids = repository.findCleanupCandidateIds(CUTOFF, PageRequest.of(0, 3));

        assertThat(ids).containsExactlyElementsOf(candidateIds.subList(0, 3)).isSorted();
        assertThat(repository.count()).isEqualTo(8);
    }

    @Test
    void eligibilityDoesNotDependOnCreationOrLastSeenTime() {
        UserSession active = saveSession(NOW.plusDays(1), null);
        active.setCreatedAt(CUTOFF.minusYears(1));
        active.setLastSeenAt(CUTOFF.minusYears(1));
        UserSession expired = saveSession(CUTOFF.minusDays(1), null);
        expired.setCreatedAt(NOW);
        expired.setLastSeenAt(NOW);
        flushAndClear();

        assertThat(repository.findCleanupCandidateIds(CUTOFF, PageRequest.of(0, 500)))
                .containsExactly(expired.getId());
    }

    @Test
    void deleteRevalidatesEligibilityAndCountsOnlyIncludedRowsActuallyDeleted() {
        Long changedId = saveSession(CUTOFF.minusDays(30), null).getId();
        Long deletedId = saveSession(NOW.plusDays(7), CUTOFF.minusDays(10)).getId();
        Long excludedExpiredId = saveSession(CUTOFF.minusDays(1), null).getId();
        Long excludedRevokedId = saveSession(NOW.plusDays(1), CUTOFF.minusDays(1)).getId();
        Long activeId = saveSession(NOW.plusDays(7), null).getId();
        flushAndClear();

        List<Long> selectedIds = repository.findCleanupCandidateIds(CUTOFF, PageRequest.of(0, 2));
        assertThat(selectedIds).containsExactly(changedId, deletedId);
        UserSession changedSession = repository.findById(changedId).orElseThrow();
        changedSession.setRevokedAt(NOW.minusDays(5));
        flushAndClear();

        List<Long> requestedIds = new ArrayList<>(selectedIds);
        requestedIds.add(activeId);
        requestedIds.add(Long.MAX_VALUE);
        int deleted = repository.deleteCleanupCandidatesByIds(requestedIds, CUTOFF);
        flushAndClear();

        assertThat(deleted).isEqualTo(1);
        assertThat(repository.findById(deletedId)).isEmpty();
        assertThat(repository.findById(changedId)).get()
                .extracting(UserSession::getRevokedAt).isEqualTo(NOW.minusDays(5));
        assertThat(repository.findAll()).extracting(UserSession::getId)
                .containsExactlyInAnyOrder(changedId, excludedExpiredId, excludedRevokedId, activeId);

        assertThat(repository.deleteCleanupCandidatesByIds(requestedIds, CUTOFF)).isZero();
        flushAndClear();
        assertThat(repository.count()).isEqualTo(4);
    }

    @Test
    void deletePreservesBothExactCutoffBoundaries() {
        Long expiredBoundaryId = saveSession(CUTOFF, null).getId();
        Long revokedBoundaryId = saveSession(CUTOFF.minusDays(10), CUTOFF).getId();
        Long eligibleId = saveSession(CUTOFF.minusSeconds(1), null).getId();
        flushAndClear();

        int deleted = repository.deleteCleanupCandidatesByIds(
                List.of(expiredBoundaryId, revokedBoundaryId, eligibleId), CUTOFF);
        flushAndClear();

        assertThat(deleted).isEqualTo(1);
        assertThat(repository.findAll()).extracting(UserSession::getId)
                .containsExactlyInAnyOrder(expiredBoundaryId, revokedBoundaryId);
    }

    @Test
    void emptyIdsDeleteNothingWithCurrentHibernateProvider() {
        Long id = saveSession(CUTOFF.minusDays(1), null).getId();
        flushAndClear();

        assertThat(repository.deleteCleanupCandidatesByIds(List.of(), CUTOFF)).isZero();
        flushAndClear();

        assertThat(repository.findById(id)).isPresent();
        assertThat(repository.count()).isEqualTo(1);
    }

    private UserSession saveSession(LocalDateTime expiresAt, LocalDateTime revokedAt) {
        return repository.save(UserSession.builder()
                .userId(1L)
                .sessionTokenHash(UUID.randomUUID().toString())
                .createdAt(CUTOFF.minusDays(60))
                .lastSeenAt(CUTOFF.minusDays(60))
                .expiresAt(expiresAt)
                .revokedAt(revokedAt)
                .build());
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
