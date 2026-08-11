package com.turnero.api.service;

import com.turnero.api.context.CurrentBusinessContext;
import com.turnero.api.dto.ServOfferingUpdateRequestDto;
import com.turnero.api.exception.ResourceNotFoundException;
import com.turnero.api.model.ServiceOffering;
import com.turnero.api.model.enums.ServiceOfferingStatus;
import com.turnero.api.repository.ServOfferingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Service
public class ServOfferingServiceImpl implements ServOfferingService {
    private final ServOfferingRepository servOfferingRepository;

    private final CurrentBusinessContext currentBusinessContext;

    @Override
    public ServiceOffering saveServiceOffering(ServiceOffering serviceOffering) {
        Long businessId = currentBusinessContext.getCurrentBusinessId();
        serviceOffering.setBusinessId(businessId);

        if (serviceOffering.getStatus() == null) {
            serviceOffering.setStatus(ServiceOfferingStatus.ACTIVE);
        }

        ServiceOffering savedServiceOffering = servOfferingRepository.save(serviceOffering);
        log.info("Service offering created with id={} for businessId={}", savedServiceOffering.getId(), businessId);
        return savedServiceOffering;
    }

    @Override
    public ServiceOffering findServiceOffering(Long id) {
        Long businessId = currentBusinessContext.getCurrentBusinessId();

        return servOfferingRepository.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Service offering not found with ID: " + id));
    }

    @Override
    public List<ServiceOffering> findAllServOffering() {
        Long businessId = currentBusinessContext.getCurrentBusinessId();
        return servOfferingRepository.findByBusinessId(businessId);
    }

    @Override
    public ServiceOffering updateServOffering(ServOfferingUpdateRequestDto servOfferigUpdateDto, Long id) {
        ServiceOffering currentServOffering = findServiceOffering(id);

        if (servOfferigUpdateDto.getName() != null) {
            currentServOffering.setName(servOfferigUpdateDto.getName());
        }

        if (servOfferigUpdateDto.getCategory() != null) {
            currentServOffering.setCategory(servOfferigUpdateDto.getCategory());
        }

        if (servOfferigUpdateDto.getDurationMinutes() != null) {
            currentServOffering.setDurationMinutes(servOfferigUpdateDto.getDurationMinutes());
        }

        if (servOfferigUpdateDto.getPriceCents() != null) {
            currentServOffering.setPriceCents(servOfferigUpdateDto.getPriceCents());
        }

        if (servOfferigUpdateDto.getStatus() != null) {
            currentServOffering.setStatus(servOfferigUpdateDto.getStatus());
        }

        ServiceOffering updatedServiceOffering = servOfferingRepository.save(currentServOffering);

        log.info("Service offering with id={} successfully updated.", id);

        return updatedServiceOffering;

    }

    @Override
    public void deleteServOffering(Long id) {
        Long businessId = currentBusinessContext.getCurrentBusinessId();
        ServiceOffering serviceOffering = servOfferingRepository.findByIdAndBusinessId(id, businessId)
                        .orElseThrow(() -> new ResourceNotFoundException("Service offering not found with ID: " + id));

        serviceOffering.setStatus(ServiceOfferingStatus.INACTIVE);

        servOfferingRepository.save(serviceOffering);

        log.info("Service offering with id={} successfully deactivated for businessId={}.", id, businessId);
    }
}
