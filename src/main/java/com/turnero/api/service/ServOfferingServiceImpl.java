package com.turnero.api.service;

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

    @Override
    public ServiceOffering saveServiceOffering(ServiceOffering serviceOffering) {
        ServiceOffering savedServiceOffering = servOfferingRepository.save(serviceOffering);
        log.info("Service offering created with id={}", serviceOffering.getId());
        return savedServiceOffering;
    }

    @Override
    public ServiceOffering findServiceOffering(Long id) {
        return servOfferingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Service offering not found with ID: " + id));
    }

    public List<ServiceOffering> findAllServOffering() {
        return servOfferingRepository.findAll();
    }

    @Override
    public void updateServOffering(ServiceOffering serviceOffering, Long id) {
        ServiceOffering currentServOffering = findServiceOffering(id);

        currentServOffering.setName(serviceOffering.getName());
        currentServOffering.setDurationMinutes(serviceOffering.getDurationMinutes());
        currentServOffering.setPrice(serviceOffering.getPrice());

        servOfferingRepository.save(currentServOffering);
        log.info("Service offering with id={} successfully updated.", id);

    }

    @Override
    public void deleteServOffering(Long id) {
        if(servOfferingRepository.existsById(id)){
            servOfferingRepository.deleteById(id);
            log.info("Service offering with id={} successfully deleted.", id);
        }else {
            throw new ResourceNotFoundException("Service offering not found with ID: " + id);
        }

    }
}
