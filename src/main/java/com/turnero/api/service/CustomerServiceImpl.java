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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Predicate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;

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
    public Customer findOrCreateCustomerForBusiness(Long businessId, String name, String email, String phoneNumber) {

        String normalizedEmail = email.trim();
        String normalizedPhoneNumber = phoneNumber.trim();

        return customerRepository.findByBusinessIdAndEmailIgnoreCase(businessId, normalizedEmail)
                .orElseGet(() -> {
                    Customer customer = new Customer();
                    customer.setBusinessId(businessId);
                    customer.setName(name.trim());
                    customer.setEmail(normalizedEmail);
                    customer.setPhoneNumber(normalizedPhoneNumber);
                    customer.setStatus(CustomerStatus.ACTIVE);
                    customer.setCreatedAt(LocalDateTime.now());
                    customer.setUpdatedAt(LocalDateTime.now());

                    return customerRepository.save(customer);
                });
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

    @Override
    public Page<Customer> listCustomers(String q, CustomerStatus status, int page, int size, Sort sort) {
        Long businessId = currentBusinessContext.getCurrentBusinessId();
        if (page < 0 || size < 1 || size > 100 || (q != null && q.length() > 100)) {
            throw new IllegalArgumentException("Invalid customer list parameter");
        }
        String search = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        Specification<Customer> filters = (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("businessId"), businessId));
            if (status != null) predicates.add(builder.equal(root.get("status"), status));
            if (!search.isEmpty()) {
                String escaped = search.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
                String pattern = "%" + escaped + "%";
                List<Predicate> matches = new ArrayList<>();
                matches.add(builder.like(builder.lower(root.get("name")), pattern, '\\'));
                matches.add(builder.like(builder.lower(root.get("email")), pattern, '\\'));
                matches.add(builder.like(root.get("phoneNumber"), pattern, '\\'));
                // Normalize only telephone-shaped queries; names/emails containing digits stay literal.
                String digits = search.replaceAll("[^0-9]", "");
                if (!digits.isEmpty() && search.matches("[0-9+().\\s-]+")) {
                    var normalizedPhone = builder.function("regexp_replace", String.class,
                            root.get("phoneNumber"), builder.literal("[^0-9]"), builder.literal(""), builder.literal("g"));
                    matches.add(builder.like(normalizedPhone, "%" + digits + "%"));
                }
                predicates.add(builder.or(matches.toArray(new Predicate[0])));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };
        return customerRepository.findAll(filters, PageRequest.of(page, size, sort));
    }

    @Override
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
