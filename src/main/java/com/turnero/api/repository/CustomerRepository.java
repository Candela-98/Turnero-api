package com.turnero.api.repository;

import com.turnero.api.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    List<Customer> findAllByBusinessId(Long businessId);

    Optional<Customer> findByIdAndBusinessId(Long id, Long businessId);

    boolean existsByIdAndBusinessId(Long customerId, Long businessId);

    Optional<Customer> findByBusinessIdAndEmailIgnoreCase(Long businessId, String email);

    Optional<Customer> findByBusinessIdAndPhoneNumber(Long businessId, String phoneNumber);
}
