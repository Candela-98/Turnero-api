package com.turnero.api.repository;

import com.turnero.api.model.StaffMember;
import com.turnero.api.model.enums.StaffMemberStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StaffMemberRepository extends JpaRepository<StaffMember, Long> {
    List<StaffMember> findAllByBusinessId(Long businessId);

    Optional<StaffMember> findByIdAndBusinessId(Long id, Long businessId);

    List<StaffMember> findAllByIdInAndBusinessId(List<Long> ids, Long businessId);

    List<StaffMember> findAllByIdInAndBusinessIdAndStatus(List<Long> ids, Long businessId, StaffMemberStatus status);

    boolean existsByIdAndBusinessId(Long id, Long businessId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT staffMember
        FROM StaffMember staffMember
        WHERE staffMember.id = :id
          AND staffMember.businessId = :businessId
        """)
    Optional<StaffMember> findByIdAndBusinessIdForUpdate(
            @Param("id") Long id,
            @Param("businessId") Long businessId
    );
}
