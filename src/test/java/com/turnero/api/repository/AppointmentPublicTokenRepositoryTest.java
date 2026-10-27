package com.turnero.api.repository;

import com.turnero.api.model.Appointment;
import com.turnero.api.model.AppointmentPublicToken;
import com.turnero.api.model.Business;
import com.turnero.api.model.enums.AppointmentPublicTokenType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.test.context.ActiveProfiles;

import java.lang.reflect.Method;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class AppointmentPublicTokenRepositoryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 16, 12, 0);
    private static final LocalDateTime CUTOFF = NOW.minusDays(30);

    @Autowired private AppointmentPublicTokenRepository repository;
    @Autowired private BusinessRepository businessRepository;
    @Autowired private EntityManager entityManager;

    @Test
    void findByTokenHashAndTypeForUpdate_isConfiguredWithPessimisticWriteLock() throws NoSuchMethodException {
        Method method = AppointmentPublicTokenRepository.class.getMethod(
                "findByTokenHashAndTypeForUpdate", String.class, AppointmentPublicTokenType.class);
        Lock lock = method.getAnnotation(Lock.class);

        assertThat(lock).isNotNull();
        assertThat(lock.value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("eligibilityCases")
    void selectsAndDeletesOnlyEligibleTokens(String description, LocalDateTime expiresAt,
                                             LocalDateTime usedAt, boolean eligible) {
        Business business = saveBusiness("America/Argentina/Buenos_Aires");
        Long id = saveToken(business, expiresAt, usedAt).getId();
        flushAndClear();

        assertThat(repository.findCleanupCandidateIds(business.getId(), CUTOFF, PageRequest.of(0, 500)))
                .isEqualTo(eligible ? List.of(id) : List.of());
        assertThat(repository.deleteCleanupCandidatesByIds(business.getId(), List.of(id), CUTOFF))
                .isEqualTo(eligible ? 1 : 0);
        flushAndClear();
        assertThat(repository.existsById(id)).isEqualTo(!eligible);
    }

    static Stream<Arguments> eligibilityCases() {
        return Stream.of(
                Arguments.of("active unused token", NOW.plusDays(7), null, false),
                Arguments.of("recent expiration", NOW.minusDays(5), null, false),
                Arguments.of("old expiration", CUTOFF.minusSeconds(1), null, true),
                Arguments.of("expiration exactly at cutoff", CUTOFF, null, false),
                Arguments.of("expiration after cutoff", CUTOFF.plusSeconds(1), null, false),
                Arguments.of("recent use protects old expiration", CUTOFF.minusDays(10), NOW, false),
                Arguments.of("old use", CUTOFF.minusDays(10), CUTOFF.minusSeconds(1), true),
                Arguments.of("use exactly at cutoff protects old expiration", CUTOFF.minusDays(10), CUTOFF, false),
                Arguments.of("use after cutoff protects old expiration", CUTOFF.minusDays(10), CUTOFF.plusSeconds(1), false),
                Arguments.of("old use with future expiration", NOW.plusDays(7), CUTOFF.minusSeconds(1), true)
        );
    }

    @Test
    void selectsIdsInAscendingOrderWithinBusinessAndRespectsConfigurableLimit() {
        Business business = saveBusiness("UTC");
        Business other = saveBusiness("UTC");
        saveToken(other, CUTOFF.minusDays(1), null);
        saveToken(business, NOW.plusDays(1), null);
        List<Long> candidates = new ArrayList<>();
        for (int index = 0; index < 7; index++) {
            candidates.add(saveToken(business, CUTOFF.minusDays(index + 1), null).getId());
        }
        flushAndClear();

        assertThat(repository.findCleanupCandidateIds(business.getId(), CUTOFF, PageRequest.of(0, 3)))
                .containsExactlyElementsOf(candidates.subList(0, 3));
        assertThat(repository.count()).isEqualTo(9);
    }

    @Test
    void callerCanShareOneGlobalBudgetAcrossBusinessesOrderedById() {
        Business first = saveBusiness("UTC");
        Business second = saveBusiness("UTC");
        Long secondId = saveToken(second, CUTOFF.minusDays(1), null).getId();
        Long firstId = saveToken(first, CUTOFF.minusDays(1), null).getId();
        Long nextFirstId = saveToken(first, CUTOFF.minusDays(1), null).getId();
        saveToken(second, CUTOFF.minusDays(1), null);
        flushAndClear();

        int globalBudget = 3;
        List<Long> selected = new ArrayList<>();
        List<Business> businesses = businessRepository.findAll(Sort.by("id"));
        assertThat(businesses).extracting(Business::getId).containsExactly(first.getId(), second.getId());
        for (Business business : businesses) {
            int remaining = globalBudget - selected.size();
            if (remaining == 0) {
                break;
            }
            selected.addAll(repository.findCleanupCandidateIds(
                    business.getId(), CUTOFF, PageRequest.of(0, remaining)));
        }

        assertThat(selected).containsExactly(firstId, nextFirstId, secondId);
    }

    @Test
    void usesEachBusinessLocalCutoffFromTheSameReferenceInstantForSelectionAndDeletion() {
        Instant reference = Instant.parse("2026-10-16T12:00:00Z");
        Business west = saveBusiness("America/Argentina/Buenos_Aires");
        Business east = saveBusiness("Asia/Tokyo");
        LocalDateTime westCutoff = LocalDateTime.ofInstant(reference, ZoneId.of(west.getTimezone())).minusDays(30);
        LocalDateTime eastCutoff = LocalDateTime.ofInstant(reference, ZoneId.of(east.getTimezone())).minusDays(30);
        LocalDateTime expiration = LocalDateTime.of(2026, 9, 16, 12, 0);
        Long westId = saveToken(west, expiration, null).getId();
        Long eastId = saveToken(east, expiration, null).getId();
        Long westUsedId = saveToken(west, CUTOFF.minusDays(5), expiration).getId();
        Long eastUsedId = saveToken(east, CUTOFF.minusDays(5), expiration).getId();
        flushAndClear();

        assertThat(repository.findCleanupCandidateIds(west.getId(), westCutoff, PageRequest.of(0, 500))).isEmpty();
        assertThat(repository.findCleanupCandidateIds(east.getId(), eastCutoff, PageRequest.of(0, 500)))
                .containsExactly(eastId, eastUsedId);
        List<Long> allIds = List.of(westId, eastId, westUsedId, eastUsedId);
        assertThat(repository.deleteCleanupCandidatesByIds(west.getId(), allIds, westCutoff)).isZero();
        assertThat(repository.deleteCleanupCandidatesByIds(east.getId(), allIds, eastCutoff)).isEqualTo(2);
        flushAndClear();
        assertThat(repository.findAll()).extracting(AppointmentPublicToken::getId)
                .containsExactlyInAnyOrder(westId, westUsedId);
    }

    @Test
    void deleteRevalidatesEligibilityAndBusinessAndOnlyDeletesRequestedIds() {
        Business business = saveBusiness("UTC");
        Business other = saveBusiness("UTC");
        Long usedMeanwhile = saveToken(business, CUTOFF.minusDays(5), null).getId();
        Long extendedMeanwhile = saveToken(business, CUTOFF.minusDays(5), null).getId();
        Long movedMeanwhile = saveToken(business, CUTOFF.minusDays(5), null).getId();
        Long deletedId = saveToken(business, NOW.plusDays(5), CUTOFF.minusDays(1)).getId();
        Long unselected = saveToken(business, CUTOFF.minusDays(1), null).getId();
        Long otherBusiness = saveToken(other, CUTOFF.minusDays(1), null).getId();
        Long active = saveToken(business, NOW.plusDays(1), null).getId();
        flushAndClear();
        List<Long> selected = repository.findCleanupCandidateIds(business.getId(), CUTOFF, PageRequest.of(0, 4));
        assertThat(selected).containsExactly(usedMeanwhile, extendedMeanwhile, movedMeanwhile, deletedId);

        repository.findById(usedMeanwhile).orElseThrow().setUsedAt(NOW);
        repository.findById(extendedMeanwhile).orElseThrow().setExpiresAt(NOW.plusDays(1));
        AppointmentPublicToken moved = repository.findById(movedMeanwhile).orElseThrow();
        entityManager.find(Appointment.class, moved.getAppointmentId()).setBusinessId(other.getId());
        flushAndClear();

        List<Long> requested = new ArrayList<>(selected);
        requested.addAll(List.of(otherBusiness, active, Long.MAX_VALUE));
        assertThat(repository.deleteCleanupCandidatesByIds(business.getId(), requested, CUTOFF)).isEqualTo(1);
        flushAndClear();
        assertThat(repository.findAll()).extracting(AppointmentPublicToken::getId)
                .containsExactlyInAnyOrder(usedMeanwhile, extendedMeanwhile, movedMeanwhile, unselected, otherBusiness, active);
        assertThat(repository.deleteCleanupCandidatesByIds(business.getId(), requested, CUTOFF)).isZero();
    }

    @Test
    void eligibilityDoesNotDependOnCreationTime() {
        Business business = saveBusiness("UTC");
        AppointmentPublicToken active = saveToken(business, NOW.plusDays(1), null);
        active.setCreatedAt(CUTOFF.minusYears(1));
        AppointmentPublicToken expired = saveToken(business, CUTOFF.minusDays(1), null);
        expired.setCreatedAt(NOW);
        flushAndClear();

        assertThat(repository.findCleanupCandidateIds(business.getId(), CUTOFF, PageRequest.of(0, 500)))
                .containsExactly(expired.getId());
        assertThat(repository.deleteCleanupCandidatesByIds(business.getId(), List.of(active.getId(), expired.getId()), CUTOFF))
                .isEqualTo(1);
        flushAndClear();
        assertThat(repository.existsById(active.getId())).isTrue();
    }

    @Test
    void emptyIdsDeleteNothingWithCurrentHibernateProvider() {
        Business business = saveBusiness("UTC");
        Long id = saveToken(business, CUTOFF.minusDays(1), null).getId();
        flushAndClear();

        assertThat(repository.deleteCleanupCandidatesByIds(business.getId(), List.of(), CUTOFF)).isZero();
        flushAndClear();
        assertThat(repository.existsById(id)).isTrue();
    }

    private Business saveBusiness(String timezone) {
        return businessRepository.save(Business.builder()
                .name("Cleanup test").slug(UUID.randomUUID().toString()).timezone(timezone).build());
    }

    private AppointmentPublicToken saveToken(Business business, LocalDateTime expiresAt, LocalDateTime usedAt) {
        Appointment appointment = Appointment.builder().businessId(business.getId())
                .startsAt(NOW.plusDays(1)).endsAt(NOW.plusDays(1).plusHours(1)).build();
        entityManager.persist(appointment);
        return repository.save(AppointmentPublicToken.builder()
                .appointmentId(appointment.getId()).tokenHash(UUID.randomUUID().toString())
                .type(AppointmentPublicTokenType.CANCEL).expiresAt(expiresAt).usedAt(usedAt)
                .createdAt(CUTOFF.minusDays(60)).build());
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
