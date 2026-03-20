package com.turnero.api.service;

import com.turnero.api.model.ServiceOffering;
import com.turnero.api.repository.ServOfferingRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class ServOfferingServiceImpl implements ServOfferingService {
    private final ServOfferingRepository servOfferingRepository;

    public ServOfferingServiceImpl(ServOfferingRepository servOfferingRepository) {
        this.servOfferingRepository = servOfferingRepository;
    }

    @Override
    public ServiceOffering saveServiceOffering(ServiceOffering serviceOffering) {
        return servOfferingRepository.save(serviceOffering);
    }

    @Override
    public ServiceOffering findServiceOffering(Long id) {
        return servOfferingRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Service offering not found"));
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
        System.out.println("Service offering with Id " + id + " successfully updated.");

    }

    @Override
    public void deleteServOffering(Long id) {
        if(servOfferingRepository.existsById(id)){
            servOfferingRepository.deleteById(id);
            System.out.println("Service offering with Id: " + id + " successfully deleted.");
        }else {
            throw new RuntimeException("Service offering not found");
        }

    }
}
