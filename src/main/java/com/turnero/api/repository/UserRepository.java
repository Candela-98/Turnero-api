package com.turnero.api.repository;

import com.turnero.api.model.User;
import com.turnero.api.model.enums.AuthProvider;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id and u.businessId = :businessId")
    Optional<User> findByIdAndBusinessIdForUpdate(@Param("id") Long id, @Param("businessId") Long businessId);

    boolean existsByIdAndBusinessId(Long id, Long businessId);

    Optional<User> findByAuthProviderAndAuthSubject(AuthProvider authProvider, String authSubject);
}
