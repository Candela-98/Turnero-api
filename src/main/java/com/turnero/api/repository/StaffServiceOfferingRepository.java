package com.turnero.api.repository;

import com.turnero.api.model.StaffServiceOffering;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StaffServiceOfferingRepository extends JpaRepository<StaffServiceOffering, Long> {
    List<StaffServiceOffering> findAllByStaffMemberId(Long staffMemberId);

    void deleteAllByStaffMemberId(Long staffMemberId);
}
