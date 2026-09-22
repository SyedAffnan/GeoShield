package com.geoshield.identity.repository;

import com.geoshield.identity.entity.Role;
import com.geoshield.identity.entity.User;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    Optional<User> findByEmail(String email);
    List<User> findAllByOrderByCreatedAtDesc();
    List<User> findAllByRoleNameOrderByCreatedAtDesc(Role roleName);
    long countByRoleName(Role roleName);

    /**
     * Acquires a pessimistic write lock (SELECT … FOR UPDATE) on the User row
     * before creating a new SOS request.  This prevents two concurrent requests
     * from the same tourist from both passing the "no active SOS" guard and
     * inserting duplicate rows.  Must be called inside a transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") UUID id);
}
