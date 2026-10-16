package com.turnero.api.scheduler;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.turnero.api.service.SessionCleanupService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class SessionCleanupSchedulerTest {

    @Mock
    private SessionCleanupService service;

    private SessionCleanupScheduler scheduler;
    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        scheduler = new SessionCleanupScheduler(service);
        logger = (Logger) LoggerFactory.getLogger(SessionCleanupScheduler.class);
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
    @ValueSource(ints = {17, 0})
    void cleanupSessions_callsServiceOnceAndLogsSuccessfulExecution(int deletedCount) {
        given(service.cleanupSessionsBatch()).willReturn(deletedCount);

        scheduler.cleanupSessions();

        verify(service).cleanupSessionsBatch();
        verifyNoMoreInteractions(service);
        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.getFirst();
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        assertThat(event.getFormattedMessage()).matches(
                "Session cleanup completed: deletedCount=" + deletedCount + ", durationMs=\\d+");
        assertThat(event.getArgumentArray()).hasSize(2);
        assertThat((Long) event.getArgumentArray()[1]).isNotNegative();
    }

    @Test
    void cleanupSessions_whenServiceFails_propagatesExceptionWithoutSuccessLog() {
        RuntimeException failure = new RuntimeException("Cleanup failed");
        given(service.cleanupSessionsBatch()).willThrow(failure);

        assertThatThrownBy(scheduler::cleanupSessions).isSameAs(failure);

        verify(service).cleanupSessionsBatch();
        verifyNoMoreInteractions(service);
        assertThat(appender.list).isEmpty();
    }
}
