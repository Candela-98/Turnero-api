package com.turnero.api.repository;

import com.turnero.api.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
    boolean existsByIdAndBusinessId(Long id, Long businessId);
}
