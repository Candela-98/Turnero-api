package com.turnero.api.repository;

import com.turnero.api.model.User;
import com.turnero.api.model.enums.AuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    boolean existsByIdAndBusinessId(Long id, Long businessId);

    Optional<User> findByAuthProviderAndAuthSubject(AuthProvider authProvider, String authSubject);
}
