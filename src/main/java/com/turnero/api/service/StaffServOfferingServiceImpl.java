package com.turnero.api.service;

import com.turnero.api.context.CurrentBusinessContext;
import com.turnero.api.dto.ServOfferingResponseDto;
import com.turnero.api.exception.AppointmentOverlapException;
import com.turnero.api.exception.ResourceNotFoundException;
import com.turnero.api.mapper.ServiceOfferingMapper;
import com.turnero.api.model.ServiceOffering;
import com.turnero.api.model.StaffServiceOffering;
import com.turnero.api.model.enums.ServiceOfferingStatus;
import com.turnero.api.repository.ServOfferingRepository;
import com.turnero.api.repository.StaffMemberRepository;
import com.turnero.api.repository.StaffServiceOfferingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@RequiredArgsConstructor
@Service
public class StaffServOfferingServiceImpl implements StaffServiceOfferingService{

    private final StaffServiceOfferingRepository staffServiceOfferingRepository;
    private final StaffMemberRepository staffMemberRepository;
    private final ServOfferingRepository servOfferingRepository;
    private final CurrentBusinessContext currentBusinessContext;
    private final ServiceOfferingMapper serviceOfferingMapper;

    @Override
    public List<ServOfferingResponseDto> getServiceOfferings(Long staffMemberId) {
        Long businessId = currentBusinessContext.getCurrentBusinessId();

        if (!staffMemberRepository.existsByIdAndBusinessId(staffMemberId, businessId)) {
            throw new ResourceNotFoundException("Staff member not found for the current business");
        }

        List<StaffServiceOffering> associations = staffServiceOfferingRepository.findAllByStaffMemberId(staffMemberId);
        List<Long> serviceOfferingIds = associations.stream()
                .map(StaffServiceOffering::getServiceOfferingId)
                .toList();
        List<ServiceOffering> serviceOfferings =
                servOfferingRepository.findAllByIdInAndBusinessId(serviceOfferingIds, businessId);

        return serviceOfferings.stream()
                .map(serviceOfferingMapper::toResponseDto)
                .toList();
    }

    @Override
    public void replaceServiceOfferings(Long staffMemberId, List<Long> serviceOfferingIds) {
        Long businessId = currentBusinessContext.getCurrentBusinessId();

        if (!staffMemberRepository.existsByIdAndBusinessId(staffMemberId, businessId)) {
            throw new ResourceNotFoundException("Staff member not found for the current business");
        }

        List<Long> uniqueServiceOfferingIds = serviceOfferingIds.stream()
                .distinct()
                .toList();

        List<ServiceOffering> serviceOfferings =
                servOfferingRepository.findAllByIdInAndBusinessId(uniqueServiceOfferingIds, businessId);

        if (serviceOfferings.size() != uniqueServiceOfferingIds.size()) {
            throw new ResourceNotFoundException("One or more service offerings were not found for the current business");
        }

        boolean hasInactiveService = serviceOfferings.stream()
                .anyMatch(serviceOffering -> serviceOffering.getStatus() == ServiceOfferingStatus.INACTIVE);

        if (hasInactiveService) {
            throw new AppointmentOverlapException("Inactive service offerings cannot be assigned to staff members");
        }

        staffServiceOfferingRepository.deleteAllByStaffMemberId(staffMemberId);

        List<StaffServiceOffering> associations = uniqueServiceOfferingIds.stream()
                .map(serviceOfferingId -> StaffServiceOffering.builder()
                        .staffMemberId(staffMemberId)
                        .serviceOfferingId(serviceOfferingId)
                        .build())
                .toList();

        staffServiceOfferingRepository.saveAll(associations);
    }
}
