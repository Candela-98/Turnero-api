package com.turnero.api.service;

import com.turnero.api.context.CurrentBusinessContext;
import com.turnero.api.exception.ResourceNotFoundException;
import com.turnero.api.model.ServiceOffering;
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
    public void updateServOffering(ServiceOffering serviceOffering, Long id) {
        ServiceOffering currentServOffering = findServiceOffering(id);

        currentServOffering.setName(serviceOffering.getName());
        currentServOffering.setCategory(serviceOffering.getCategory());
        currentServOffering.setDurationMinutes(serviceOffering.getDurationMinutes());
        currentServOffering.setPriceCents(serviceOffering.getPriceCents());
        currentServOffering.setStatus(serviceOffering.getStatus());

        servOfferingRepository.save(currentServOffering);
        log.info("Service offering with id={} successfully updated.", id);

    }

    @Override
    public void deleteServOffering(Long id) {
        Long businessId = currentBusinessContext.getCurrentBusinessId();
        if(servOfferingRepository.existsByIdAndBusinessId(id, businessId)) {
            servOfferingRepository.deleteById(id);
            log.info("Service offering with id={} successfully deleted for businessId={}.", id, businessId);
        }else {
            throw new ResourceNotFoundException("Service offering not found with ID: " + id);
        }

    }
}
