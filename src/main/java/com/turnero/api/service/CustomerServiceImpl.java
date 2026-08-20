package com.turnero.api.service;

import com.turnero.api.context.CurrentBusinessContext;
import com.turnero.api.dto.CustomerUpdateRequestDto;
import com.turnero.api.exception.ResourceNotFoundException;
import com.turnero.api.model.Customer;
import com.turnero.api.model.enums.CustomerStatus;
import com.turnero.api.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Service
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository customerRepository;

    private final CurrentBusinessContext currentBusinessContext;

    @Override
    public Customer saveCustomer(Customer customer) {
        Long businessId = currentBusinessContext.getCurrentBusinessId();

        customer.setBusinessId(businessId);
        customer.setStatus(CustomerStatus.ACTIVE);
        customer.setCreatedAt(LocalDateTime.now());
        customer.setUpdatedAt(LocalDateTime.now());

        Customer savedCustomer = customerRepository.save(customer);

        log.info("Customer created with id={}", savedCustomer.getId());
        return savedCustomer;
    }

    @Override
    public Customer findCustomer(Long id) {
        Long businessId = currentBusinessContext.getCurrentBusinessId();

        return customerRepository.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with ID: " + id));
    }

    @Override
    public Customer updateCustomer(CustomerUpdateRequestDto customer, Long id) {
        Customer currentCustomer = findCustomer(id);

        if (customer.getName() != null) {
            currentCustomer.setName(customer.getName());
        }

        if (customer.getEmail() != null) {
            currentCustomer.setEmail(customer.getEmail());
        }

        if (customer.getPhoneNumber() != null) {
            currentCustomer.setPhoneNumber(customer.getPhoneNumber());
        }

        if (customer.getInternalNotes() != null) {
            currentCustomer.setInternalNotes(customer.getInternalNotes());
        }

        if (customer.getStatus() != null) {
            currentCustomer.setStatus(customer.getStatus());
        }

        currentCustomer.setUpdatedAt(LocalDateTime.now());

        Customer updatedCustomer = customerRepository.save(currentCustomer);
        log.info("Customer with id={} successfully updated", id);
        return updatedCustomer;
    }

    public List<Customer> findAllCustomer() {
        Long businessId = currentBusinessContext.getCurrentBusinessId();
        return customerRepository.findAllByBusinessId(businessId);
    }

    @Override
    public void deleteCustomer(Long id) {
        Customer customer = findCustomer(id);

        customer.setStatus(CustomerStatus.INACTIVE);
        customer.setUpdatedAt(LocalDateTime.now());

        customerRepository.save(customer);
        log.info("Customer with id={} successfully deactivated for businessId={}", id, customer.getBusinessId());
    }
}
