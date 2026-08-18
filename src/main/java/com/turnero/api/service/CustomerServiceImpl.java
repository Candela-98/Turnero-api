package com.turnero.api.service;

import com.turnero.api.context.CurrentBusinessContext;
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
        return customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
    }

    @Override
    public void updateCustomer(Customer customer, Long id) {
        Customer currentCustomer = findCustomer(id);

        currentCustomer.setName(customer.getName());
        currentCustomer.setEmail(customer.getEmail());
        currentCustomer.setPhoneNumber(customer.getPhoneNumber());
        currentCustomer.setUpdatedAt(LocalDateTime.now());

        customerRepository.save(currentCustomer);
        log.info("Customer with id={} successfully updated", id);
    }

    public List<Customer> findAllCustomer() {
        Long businessId = currentBusinessContext.getCurrentBusinessId();
        return customerRepository.findAllByBusinessId(businessId);
    }

    @Override
    public void deleteCustomer(Long id) {

        if(customerRepository.existsById(id)) {
            customerRepository.deleteById(id);
            log.info("Customer with id={} successfully removed", id);
        } else {
            throw new ResourceNotFoundException("Customer not found with ID: " + id);
        }
    }
}
