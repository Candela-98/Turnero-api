package com.turnero.api.repository;

import com.turnero.api.model.AppointmentPublicToken;
import com.turnero.api.model.enums.AppointmentPublicTokenType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AppointmentPublicTokenRepository extends JpaRepository<AppointmentPublicToken, Long> {

    Optional<AppointmentPublicToken> findByTokenHashAndType(
            String tokenHash,
            AppointmentPublicTokenType type
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT t
        FROM AppointmentPublicToken t
        WHERE t.tokenHash = :tokenHash
          AND t.type = :type
        """)
    Optional<AppointmentPublicToken> findByTokenHashAndTypeForUpdate(
            @Param("tokenHash") String tokenHash,
            @Param("type") AppointmentPublicTokenType type
    );
}
