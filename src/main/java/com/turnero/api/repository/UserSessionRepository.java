package com.turnero.api.repository;

import com.turnero.api.model.UserSession;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserSessionRepository extends JpaRepository<UserSession, Long> {
    Optional<UserSession> findBySessionTokenHash(String sessionTokenHash);

    @Query("""
            SELECT s.id
            FROM UserSession s
            WHERE (s.revokedAt IS NOT NULL AND s.revokedAt < :cutoff)
               OR (s.revokedAt IS NULL AND s.expiresAt < :cutoff)
            ORDER BY s.id ASC
            """)
    List<Long> findCleanupCandidateIds(@Param("cutoff") LocalDateTime cutoff, Pageable pageable);

    @Modifying
    @Query("""
            DELETE FROM UserSession s
            WHERE s.id IN :ids
              AND (
                    (s.revokedAt IS NOT NULL AND s.revokedAt < :cutoff)
                    OR (s.revokedAt IS NULL AND s.expiresAt < :cutoff)
                  )
            """)
    int deleteCleanupCandidatesByIds(@Param("ids") List<Long> ids, @Param("cutoff") LocalDateTime cutoff);
}
