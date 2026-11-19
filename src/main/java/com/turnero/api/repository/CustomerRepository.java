package com.turnero.api.repository;

import com.turnero.api.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long>, JpaSpecificationExecutor<Customer> {

    @org.springframework.data.jpa.repository.Query(value = "SELECT * FROM customers WHERE id = :id AND business_id = :businessId FOR UPDATE", nativeQuery = true)
    Optional<Customer> findByIdAndBusinessIdForUpdate(Long id, Long businessId);

    @org.springframework.data.jpa.repository.Query(value = """
            SELECT * FROM customers WHERE business_id = :businessId AND (
              (:email <> '' AND lower(trim(email)) = :email) OR
              (:phone <> '' AND regexp_replace(coalesce(phone_number, ''), '[^0-9]', '', 'g') = :phone))
            ORDER BY id FOR UPDATE
            """, nativeQuery = true)
    List<Customer> findContactMatchesForUpdate(Long businessId, String email, String phone);

    Optional<Customer> findByBusinessIdAndUserId(Long businessId, Long userId);

    @org.springframework.data.jpa.repository.Query("""
            select c from Customer c
            where c.businessId = :businessId and lower(trim(c.email)) = :email
            order by c.id
            """)
    List<Customer> findEmailMatches(Long businessId, String email);

    List<Customer> findAllByBusinessId(Long businessId);

    Optional<Customer> findByIdAndBusinessId(Long id, Long businessId);

    boolean existsByIdAndBusinessId(Long customerId, Long businessId);

    Optional<Customer> findByBusinessIdAndEmailIgnoreCase(Long businessId, String email);

    Optional<Customer> findByBusinessIdAndPhoneNumber(Long businessId, String phoneNumber);
}
