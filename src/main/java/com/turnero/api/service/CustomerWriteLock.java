package com.turnero.api.service;

import com.turnero.api.exception.ResourceNotFoundException;
import com.turnero.api.model.Business;
import jakarta.persistence.EntityManager;
import com.turnero.api.repository.BusinessRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CustomerWriteLock {
    private final BusinessRepository businessRepository;
    private final EntityManager entityManager;

    @Transactional(propagation = Propagation.MANDATORY)
    public Business acquire(Long businessId) {
        Business business = businessRepository.findByIdForCustomerWrite(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Business not found"));
        entityManager.refresh(business);
        return business;
    }
}
