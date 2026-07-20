package com.geoshield.identity.repository;

import com.geoshield.identity.entity.Role;
import com.geoshield.identity.entity.UserRole;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<UserRole, UUID> {
    Optional<UserRole> findByName(Role name);
}
