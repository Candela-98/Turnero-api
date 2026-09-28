package com.turnero.api.repository;

import com.turnero.api.model.enums.AppointmentPublicTokenType;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class AppointmentPublicTokenRepositoryTest {

    @Test
    void findByTokenHashAndTypeForUpdate_isConfiguredWithPessimisticWriteLock() throws NoSuchMethodException {
        Method method = AppointmentPublicTokenRepository.class.getMethod(
                "findByTokenHashAndTypeForUpdate",
                String.class,
                AppointmentPublicTokenType.class
        );

        Lock lock = method.getAnnotation(Lock.class);

        assertThat(lock).isNotNull();
        assertThat(lock.value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }
}
