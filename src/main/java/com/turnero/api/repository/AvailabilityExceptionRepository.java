package com.turnero.api.repository;

import com.turnero.api.model.AvailabilityException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface AvailabilityExceptionRepository extends JpaRepository<AvailabilityException, Long> {

    @Query("""
            SELECT exception
            FROM AvailabilityException exception
            WHERE exception.businessId = :businessId
              AND (
                    exception.staffMemberId IS NULL
                    OR exception.staffMemberId = :staffMemberId
                  )
              AND exception.date BETWEEN :dateFrom AND :dateTo
            """)
    List<AvailabilityException> findRelevantExceptions(
            @Param("businessId") Long businessId,
            @Param("staffMemberId") Long staffMemberId,
            @Param("dateFrom") LocalDate from,
            @Param("dateTo") LocalDate to
    );

    List<AvailabilityException> findAllByBusinessIdAndDateBetween(Long businessId, LocalDate from, LocalDate to);
}
