package com.turnero.api.scheduler;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.turnero.api.service.PublicTokenCleanupService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PublicTokenCleanupSchedulerTest {

    @Mock private PublicTokenCleanupService service;

    private PublicTokenCleanupScheduler scheduler;
    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        scheduler = new PublicTokenCleanupScheduler(service);
        logger = (Logger) LoggerFactory.getLogger(PublicTokenCleanupScheduler.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        appender.stop();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 17, 500})
    void cleanupPublicTokens_callsServiceOnceAndLogsOnlyDeletedCountAndDuration(int deletedCount) {
        given(service.cleanupPublicTokensBatch()).willReturn(deletedCount);

        scheduler.cleanupPublicTokens();

        verify(service).cleanupPublicTokensBatch();
        verifyNoMoreInteractions(service);
        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.getFirst();
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        assertThat(event.getMessage()).isEqualTo("Public token cleanup completed: deletedCount={}, durationMs={}");
        assertThat(event.getFormattedMessage()).matches(
                "Public token cleanup completed: deletedCount=" + deletedCount + ", durationMs=\\d+");
        assertThat(event.getArgumentArray()).hasSize(2);
        assertThat(event.getArgumentArray()[0]).isEqualTo(deletedCount);
        assertThat(event.getArgumentArray()[1]).isInstanceOf(Long.class);
        assertThat((Long) event.getArgumentArray()[1]).isNotNegative();
        assertThat(event.getThrowableProxy()).isNull();
        assertThat(event.getKeyValuePairs()).isNullOrEmpty();
    }

    @Test
    void cleanupPublicTokens_whenServiceFails_doesNotLogSensitiveExceptionOrSuccessMessage() {
        RuntimeException failure = new RuntimeException(
                "token=secret-token tokenHash=secret-hash email=person@example.test name=Private Person");
        given(service.cleanupPublicTokensBatch()).willThrow(failure);

        assertThatThrownBy(scheduler::cleanupPublicTokens).isSameAs(failure);

        verify(service).cleanupPublicTokensBatch();
        verifyNoMoreInteractions(service);
        assertThat(appender.list).isEmpty();
    }

    @Test
    void cleanupPublicTokens_usesConfiguredCronProperty() throws NoSuchMethodException {
        Scheduled scheduled = PublicTokenCleanupScheduler.class.getMethod("cleanupPublicTokens")
                .getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.cron()).isEqualTo("${public-token.cleanup.cron}");
        verifyNoInteractions(service);
    }

    @Test
    void cleanupPublicTokens_repeatedExecutionsCallServiceOnceEachAndLogIndependentResults() {
        given(service.cleanupPublicTokensBatch()).willReturn(2, 0);

        scheduler.cleanupPublicTokens();
        scheduler.cleanupPublicTokens();

        verify(service, times(2)).cleanupPublicTokensBatch();
        verifyNoMoreInteractions(service);
        assertThat(appender.list).hasSize(2);
        assertThat(appender.list.get(0).getFormattedMessage())
                .matches("Public token cleanup completed: deletedCount=2, durationMs=\\d+");
        assertThat(appender.list.get(1).getFormattedMessage())
                .matches("Public token cleanup completed: deletedCount=0, durationMs=\\d+");
    }
}
