package com.geoshield.identity.repository;

import com.geoshield.identity.entity.Role;
import com.geoshield.identity.entity.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    Optional<User> findByEmail(String email);
    List<User> findAllByOrderByCreatedAtDesc();
    List<User> findAllByRoleNameOrderByCreatedAtDesc(Role roleName);
    long countByRoleName(Role roleName);
}

