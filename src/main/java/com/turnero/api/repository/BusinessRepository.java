package com.turnero.api.repository;

import com.turnero.api.model.Business;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BusinessRepository extends JpaRepository<Business, Long> {

    Optional<Business> findBySlug(String slug);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select b from Business b where b.id = :id")
    Optional<Business> findByIdForCustomerWrite(Long id);
}
