package com.turnero.api.repository;

import com.turnero.api.model.AppointmentPublicToken;
import com.turnero.api.model.enums.AppointmentPublicTokenType;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
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

    /**
     * The caller supplies the cutoff in the business time zone and limits the page
     * to the remaining global batch budget, shared across businesses ordered by ID.
     */
    @Query("""
            SELECT t.id
            FROM AppointmentPublicToken t
            WHERE t.appointmentId IN (
                SELECT a.id FROM Appointment a WHERE a.businessId = :businessId
            )
              AND (
                    (t.usedAt IS NOT NULL AND t.usedAt < :cutoff)
                    OR (t.usedAt IS NULL AND t.expiresAt < :cutoff)
                  )
            ORDER BY t.id ASC
            """)
    List<Long> findCleanupCandidateIds(@Param("businessId") Long businessId,
                                       @Param("cutoff") LocalDateTime cutoff, Pageable pageable);

    /**
     * Revalidates ownership and eligibility using the same business-local cutoff
     * as selection. The caller runs selection and deletion in one transaction.
     */
    @Modifying
    @Query("""
            DELETE FROM AppointmentPublicToken t
            WHERE t.id IN :ids
              AND t.appointmentId IN (
                SELECT a.id FROM Appointment a WHERE a.businessId = :businessId
              )
              AND (
                    (t.usedAt IS NOT NULL AND t.usedAt < :cutoff)
                    OR (t.usedAt IS NULL AND t.expiresAt < :cutoff)
                  )
            """)
    int deleteCleanupCandidatesByIds(@Param("businessId") Long businessId,
                                     @Param("ids") List<Long> ids,
                                     @Param("cutoff") LocalDateTime cutoff);
}
