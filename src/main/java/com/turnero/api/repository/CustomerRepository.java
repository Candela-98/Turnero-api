package com.turnero.api.repository;

import com.turnero.api.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    List<Customer> findAllByBusinessId(Long businessId);

}
