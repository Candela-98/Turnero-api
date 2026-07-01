package com.turnero.api.repository;

import com.turnero.api.model.StaffMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StaffMemberRepository extends JpaRepository<StaffMember, Long> {
    List<StaffMember> findAllByBusinessId(Long businessId);

    boolean existsByIdAndBusinessId(Long id, Long businessId);
}
