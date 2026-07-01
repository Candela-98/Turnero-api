package com.turnero.api.repository;

import com.turnero.api.model.ServiceOffering;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ServOfferingRepository extends JpaRepository<ServiceOffering, Long>{
    List<ServiceOffering> findByBusinessId(Long businessId);

    Optional<ServiceOffering> findByIdAndBusinessId(Long id, Long businessId);

    boolean existsByIdAndBusinessId(Long id, Long businessId);

    List<ServiceOffering> findAllByIdInAndBusinessId(List<Long> ids, Long businessId);
}
