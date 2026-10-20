package com.turnero.api.service;

import com.turnero.api.context.CurrentBusinessContext;
import com.turnero.api.dto.ServOfferingResponseDto;
import com.turnero.api.exception.InvalidStateTransitionException;
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
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class StaffServOfferingServiceImpl implements StaffServiceOfferingService {

    private final StaffServiceOfferingRepository staffServiceOfferingRepository;
    private final StaffMemberRepository staffMemberRepository;
    private final ServOfferingRepository servOfferingRepository;
    private final CurrentBusinessContext currentBusinessContext;
    private final ServiceOfferingMapper serviceOfferingMapper;

    @Override
    @Transactional(readOnly = true)
    public List<ServOfferingResponseDto> getServiceOfferings(Long staffMemberId) {
        Long businessId = currentBusinessContext.getCurrentBusinessId();

        if (!staffMemberRepository.existsByIdAndBusinessId(staffMemberId, businessId)) {
            throw new ResourceNotFoundException("Staff member not found for the current business");
        }

        List<StaffServiceOffering> associations = staffServiceOfferingRepository.findAllByStaffMemberId(staffMemberId);
        List<Long> serviceOfferingIds = associations.stream()
                .map(StaffServiceOffering::getServiceOfferingId)
                .toList();
        if (serviceOfferingIds.isEmpty()) return List.of();
        List<ServiceOffering> serviceOfferings =
                servOfferingRepository.findAllByIdInAndBusinessId(serviceOfferingIds, businessId);

        return serviceOfferings.stream()
                .map(serviceOfferingMapper::toResponseDto)
                .toList();
    }

    @Override
    @Transactional
    public void replaceServiceOfferings(Long staffMemberId, List<Long> serviceOfferingIds) {
        if (serviceOfferingIds == null || serviceOfferingIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException("Service offering IDs must be a list of positive, non-null IDs");
        }
        Long businessId = currentBusinessContext.getCurrentBusinessId();
        // Serialize replacements for this professional, including the empty selection.
        staffMemberRepository.findByIdAndBusinessIdForUpdate(staffMemberId, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Staff member not found for the current business"));

        List<Long> requested = serviceOfferingIds.stream().distinct().sorted().toList();
        var existing = staffServiceOfferingRepository.findAllByStaffMemberId(staffMemberId);
        var existingIds = existing.stream().map(StaffServiceOffering::getServiceOfferingId).collect(Collectors.toSet());
        // Match the locks used by service state changes; deterministic order avoids inverted locks.
        List<ServiceOffering> offerings = requested.isEmpty() ? List.of()
                : servOfferingRepository.findAllForUpdate(requested, businessId);
        if (offerings.size() != requested.size()) {
            throw new ResourceNotFoundException("One or more service offerings were not found for the current business");
        }
        if (offerings.stream().anyMatch(service -> service.getStatus() == ServiceOfferingStatus.INACTIVE
                && !existingIds.contains(service.getId()))) {
            throw new InvalidStateTransitionException("Inactive service offerings cannot be newly assigned to staff members");
        }
        var requestedIds = new HashSet<>(requested);
        var removed = existing.stream().filter(association -> !requestedIds.contains(association.getServiceOfferingId())).toList();
        var added = requested.stream().filter(id -> !existingIds.contains(id))
                .map(id -> StaffServiceOffering.builder().staffMemberId(staffMemberId).serviceOfferingId(id).build()).toList();
        // Retain unchanged rows and never touch appointments. Validation precedes all mutations.
        if (!removed.isEmpty()) staffServiceOfferingRepository.deleteAll(removed);
        if (!added.isEmpty()) staffServiceOfferingRepository.saveAll(added);
    }
}
