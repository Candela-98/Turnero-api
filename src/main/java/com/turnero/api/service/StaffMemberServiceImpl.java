package com.turnero.api.service;

import com.turnero.api.exception.ResourceNotFoundException;
import com.turnero.api.model.StaffMember;
import com.turnero.api.repository.StaffMemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Service
public class StaffMemberServiceImpl implements StaffMemberService {

    private final StaffMemberRepository staffMemberRepository;

    @Override
    public StaffMember saveStaffMember(StaffMember staffMember) {
        StaffMember savedStaffMember = staffMemberRepository.save(staffMember);
        log.info("Staff member created with id={}", savedStaffMember.getId());
        return savedStaffMember;
    }

    @Override
    public StaffMember findStaffMember(Long id) {
        return staffMemberRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Staffmember not found with ID: " + id));
    }

    @Override
    public void updateStaffMember(StaffMember staffMember, Long id) {
        StaffMember staffMemberExist = findStaffMember(id);

        staffMemberExist.setName(staffMember.getName());
        staffMemberExist.setSpecialty(staffMember.getSpecialty());
        staffMemberExist.setLicense(staffMember.getLicense());

        staffMemberRepository.save(staffMemberExist);
        log.info("Staffmember with id={} successfully updated.", id);
    }

    public List<StaffMember> findAllStaffMember() {
        return staffMemberRepository.findAll();
    }

    @Override
    public void deleteStaffMember(Long id) {
        if(staffMemberRepository.existsById(id)) {
            staffMemberRepository.deleteById(id);
            log.info("Staffmember with id={} successfully deleted.", id);
        } else {
            throw new ResourceNotFoundException("Staffmember not found with ID: " + id);
        }

    }
}
