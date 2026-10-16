package com.turnero.api.repository;

import com.turnero.api.model.ServiceOffering;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ServOfferingRepository extends JpaRepository<ServiceOffering, Long>, JpaSpecificationExecutor<ServiceOffering>{
    @Query("select distinct s.category from ServiceOffering s where s.businessId = :businessId and s.deletedAt is null and s.category is not null and s.category <> '' order by s.category")
    List<String> findCategoriesByBusinessId(@Param("businessId") Long businessId);
    @Query("select s from ServiceOffering s where s.businessId = :businessId and s.deletedAt is null")
    List<ServiceOffering> findByBusinessId(Long businessId);

    // Historical lookup includes removed services; catalog callers must filter deletedAt.
    Optional<ServiceOffering> findByIdAndBusinessId(Long id, Long businessId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ServiceOffering s where s.id = :id and s.businessId = :businessId and s.deletedAt is null")
    Optional<ServiceOffering> findForUpdate(Long id, Long businessId);

    @Query("select (count(s) > 0) from ServiceOffering s where s.id = :id and s.businessId = :businessId and s.deletedAt is null")
    boolean existsByIdAndBusinessId(Long id, Long businessId);

    @Query("select s from ServiceOffering s where s.id in :ids and s.businessId = :businessId and s.deletedAt is null")
    List<ServiceOffering> findAllByIdInAndBusinessId(List<Long> ids, Long businessId);
}
