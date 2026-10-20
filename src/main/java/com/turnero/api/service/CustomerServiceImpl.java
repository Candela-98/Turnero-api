package com.turnero.api.service;

import com.turnero.api.context.CurrentBusinessContext;
import com.turnero.api.dto.CustomerUpdateRequestDto;
import com.turnero.api.exception.ResourceNotFoundException;
import com.turnero.api.exception.CustomerBookingBlockedException;
import com.turnero.api.exception.CustomerIdentityAmbiguousException;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
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

    private final EntityManager entityManager;

    @Override
    public Customer saveCustomer(Customer customer) {
        Long businessId = currentBusinessContext.getCurrentBusinessId();

        customer.setBusinessId(businessId);
        customer.setName(customer.getName().trim());
        customer.setPhoneNumber(customer.getPhoneNumber().trim());
        customer.setEmail(optionalText(customer.getEmail()));
        customer.setInternalNotes(optionalText(customer.getInternalNotes()));
        customer.setStatus(CustomerStatus.ACTIVE);
        customer.setCreatedAt(LocalDateTime.now());
        customer.setUpdatedAt(LocalDateTime.now());

        Customer savedCustomer = customerRepository.save(customer);

        log.info("Customer created with id={}", savedCustomer.getId());
        return savedCustomer;
    }

    @Override
    @Transactional
    public Customer findOrCreateCustomerForBusiness(Long businessId, String name, String email, String phoneNumber) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        String normalizedPhone = phoneDigits(phoneNumber);
        List<Customer> matches = customerRepository.findContactMatchesForUpdate(businessId, normalizedEmail, normalizedPhone);
        // Refresh even when a caller already loaded the entity before waiting for its row lock.
        matches.forEach(entityManager::refresh);
        if (matches.stream().anyMatch(Customer::isPublicBookingBlocked)) throw new CustomerBookingBlockedException();
        List<Customer> emailMatches = matches.stream().filter(customer -> customer.getEmail() != null
                && customer.getEmail().trim().equalsIgnoreCase(normalizedEmail)).toList();
        List<Customer> candidates = emailMatches.isEmpty() ? matches : emailMatches;
        if (candidates.size() > 1) throw new CustomerIdentityAmbiguousException();
        if (!candidates.isEmpty()) {
            Customer customer = candidates.getFirst();
            if (customer.getStatus() == CustomerStatus.INACTIVE) {
                customer.setStatus(CustomerStatus.ACTIVE);
                customer.setUpdatedAt(LocalDateTime.now());
                customerRepository.save(customer);
            }
            return customer;
        }
        Customer customer = Customer.builder().businessId(businessId).name(name.trim()).email(email.trim())
                .phoneNumber(phoneNumber.trim()).status(CustomerStatus.ACTIVE)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
        return customerRepository.save(customer);
    }

    private static String phoneDigits(String phone) {
        return phone == null ? "" : phone.replaceAll("[^0-9]", "");
    }

    private Customer lockCustomer(Long id) {
        Long businessId = currentBusinessContext.getCurrentBusinessId();
        Customer customer = customerRepository.findByIdAndBusinessIdForUpdate(id, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with ID: " + id));
        entityManager.refresh(customer);
        return customer;
    }

    @Override
    public Customer findCustomer(Long id) {
        Long businessId = currentBusinessContext.getCurrentBusinessId();

        return customerRepository.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with ID: " + id));
    }

    @Override
    @Transactional
    public Customer updateCustomer(CustomerUpdateRequestDto customer, Long id) {
        Customer currentCustomer = lockCustomer(id);

        if (customer.getName() != null) {
            currentCustomer.setName(customer.getName().trim());
        }

        if (customer.getEmail() != null) {
            currentCustomer.setEmail(optionalText(customer.getEmail()));
        }

        if (customer.getPhoneNumber() != null) {
            currentCustomer.setPhoneNumber(customer.getPhoneNumber().trim());
        }

        if (customer.getInternalNotes() != null) {
            currentCustomer.setInternalNotes(optionalText(customer.getInternalNotes()));
        }

        if (customer.getStatus() != null) {
            currentCustomer.setStatus(customer.getStatus());
        }

        boolean blocked = customer.getPublicBookingBlocked() == null
                ? currentCustomer.isPublicBookingBlocked() : customer.getPublicBookingBlocked();
        String reason = customer.getPublicBookingBlockReason() == null
                ? currentCustomer.getPublicBookingBlockReason() : optionalText(customer.getPublicBookingBlockReason());
        if (blocked) {
            if (reason == null || reason.isBlank() || reason.length() > 500) {
                throw new IllegalArgumentException("A blocking reason of 1–500 characters is required");
            }
            if (optionalText(currentCustomer.getEmail()) == null && phoneDigits(currentCustomer.getPhoneNumber()).isEmpty()) {
                throw new IllegalArgumentException("An email or usable phone number is required to block public bookings");
            }
        } else if (customer.getPublicBookingBlocked() == null && customer.getPublicBookingBlockReason() != null) {
            throw new IllegalArgumentException("A blocking reason requires public_booking_blocked=true");
        }
        currentCustomer.setPublicBookingBlocked(blocked);
        currentCustomer.setPublicBookingBlockReason(blocked ? reason : null);
        currentCustomer.setUpdatedAt(LocalDateTime.now());

        Customer updatedCustomer = customerRepository.save(currentCustomer);
        log.info("Customer with id={} successfully updated", id);
        return updatedCustomer;
    }

    private static String optionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    @Override
    public Page<Customer> listCustomers(String q, CustomerStatus status, int page, int size, Sort sort) {
        return listCustomers(q, status, null, page, size, sort);
    }

    @Override
    public Page<Customer> listCustomers(String q, CustomerStatus status, Boolean blocked, int page, int size, Sort sort) {
        Long businessId = currentBusinessContext.getCurrentBusinessId();
        if (page < 0 || size < 1 || size > 100 || (q != null && q.length() > 100)) {
            throw new IllegalArgumentException("Invalid customer list parameter");
        }
        String search = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        Specification<Customer> filters = (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("businessId"), businessId));
            if (status != null) predicates.add(builder.equal(root.get("status"), status));
            if (blocked != null) predicates.add(builder.equal(root.get("publicBookingBlocked"), blocked));
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
    @Transactional
    public void deleteCustomer(Long id) {
        Customer customer = lockCustomer(id);

        customer.setStatus(CustomerStatus.INACTIVE);
        customer.setUpdatedAt(LocalDateTime.now());

        customerRepository.save(customer);
        log.info("Customer with id={} successfully archived for businessId={}", id, customer.getBusinessId());
    }
}
