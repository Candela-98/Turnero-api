package com.turnero.api.repository;

import com.turnero.api.model.StaffMember;
import com.turnero.api.model.enums.StaffMemberStatus;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StaffMemberRepository extends JpaRepository<StaffMember, Long>, JpaSpecificationExecutor<StaffMember> {
    interface ServiceCount {
        Long getStaffMemberId();
        Long getServiceCount();
    }

    @Query("""
        select association.staffMemberId as staffMemberId, count(association.id) as serviceCount
        from StaffServiceOffering association
        join StaffMember staff on staff.id = association.staffMemberId
        join ServiceOffering offering on offering.id = association.serviceOfferingId
        where staff.businessId = :businessId and offering.businessId = :businessId
          and staff.deletedAt is null and offering.deletedAt is null and staff.id in :staffIds
        group by association.staffMemberId
        """)
    List<ServiceCount> countServicesForStaff(@Param("staffIds") List<Long> staffIds, @Param("businessId") Long businessId);

    boolean existsByUserIdAndDeletedAtIsNull(Long userId);

    Optional<StaffMember> findByUserIdAndBusinessIdAndDeletedAtIsNull(Long userId, Long businessId);

    List<StaffMember> findAllByBusinessId(Long businessId);

    Optional<StaffMember> findByIdAndBusinessId(Long id, Long businessId);

    List<StaffMember> findAllByIdInAndBusinessId(List<Long> ids, Long businessId);

    List<StaffMember> findAllByIdInAndBusinessIdAndStatus(List<Long> ids, Long businessId, StaffMemberStatus status);

    @Query("select (count(staff) > 0) from StaffMember staff where staff.id = :id and staff.businessId = :businessId and staff.deletedAt is null")
    boolean existsByIdAndBusinessId(@Param("id") Long id, @Param("businessId") Long businessId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT staffMember
        FROM StaffMember staffMember
        WHERE staffMember.id = :id
          AND staffMember.businessId = :businessId
          AND staffMember.deletedAt IS NULL
        """)
    Optional<StaffMember> findByIdAndBusinessIdForUpdate(
            @Param("id") Long id,
            @Param("businessId") Long businessId
    );
}
