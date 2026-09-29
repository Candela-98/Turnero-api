package com.turnero.api.repository;

import com.turnero.api.model.ServiceOffering;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ServOfferingRepository extends JpaRepository<ServiceOffering, Long>, JpaSpecificationExecutor<ServiceOffering>{
    @Query("select distinct s.category from ServiceOffering s where s.businessId = :businessId and s.category is not null and s.category <> '' order by s.category")
    List<String> findCategoriesByBusinessId(@Param("businessId") Long businessId);
    List<ServiceOffering> findByBusinessId(Long businessId);

    Optional<ServiceOffering> findByIdAndBusinessId(Long id, Long businessId);

    boolean existsByIdAndBusinessId(Long id, Long businessId);

    List<ServiceOffering> findAllByIdInAndBusinessId(List<Long> ids, Long businessId);
}
