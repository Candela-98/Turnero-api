package com.turnero.api.repository;

import com.turnero.api.model.StaffWorkingHours;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StaffWorkingHoursRepository extends JpaRepository<StaffWorkingHours, Long> {
    List<StaffWorkingHours> findAllByStaffMemberId(Long staffMemberId);

    List<StaffWorkingHours> findAllByStaffMemberIdOrderByDayOfWeekAsc(Long staffMemberId);

    void deleteAllByStaffMemberId(Long staffMemberId);
}
