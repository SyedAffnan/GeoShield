package com.geoshield.identity.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.geoshield.identity.entity.Role;
import com.geoshield.identity.entity.User;
import com.geoshield.identity.entity.UserRole;
import com.geoshield.identity.repository.RoleRepository;
import com.geoshield.identity.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class DataInitializerTest {
    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PasswordEncoder passwordEncoder;

    private DataInitializer dataInitializer;

    @BeforeEach
    void setUp() {
        dataInitializer = new DataInitializer(userRepository, roleRepository, passwordEncoder, true);
    }

    @Test
    void seedsDefaultAdminAndResponderWhenNoneExist() {
        when(userRepository.countByRoleName(Role.ADMIN)).thenReturn(0L);
        when(userRepository.existsByEmail("admin@geoshield.com")).thenReturn(false);
        when(roleRepository.findByName(Role.ADMIN)).thenReturn(Optional.of(new UserRole(Role.ADMIN)));

        when(userRepository.countByRoleName(Role.RESPONDER)).thenReturn(0L);
        when(userRepository.existsByEmail("responder@geoshield.com")).thenReturn(false);
        when(roleRepository.findByName(Role.RESPONDER)).thenReturn(Optional.of(new UserRole(Role.RESPONDER)));

        when(passwordEncoder.encode("Admin@123456")).thenReturn("hashed_admin_pass");
        when(passwordEncoder.encode("Responder@123456")).thenReturn("hashed_resp_pass");

        dataInitializer.run(null);

        verify(userRepository).save(org.mockito.ArgumentMatchers.argThat(u ->
                u.getEmail().equals("admin@geoshield.com") && u.getRole().getName() == Role.ADMIN));
        verify(userRepository).save(org.mockito.ArgumentMatchers.argThat(u ->
                u.getEmail().equals("responder@geoshield.com") && u.getRole().getName() == Role.RESPONDER));
    }

    @Test
    void skipsSeedingWhenAdminAndResponderAlreadyExist() {
        when(userRepository.countByRoleName(Role.ADMIN)).thenReturn(1L);
        when(userRepository.countByRoleName(Role.RESPONDER)).thenReturn(1L);

        dataInitializer.run(null);

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void skipsSeedingWhenDevSeedDisabled() {
        DataInitializer disabledInitializer = new DataInitializer(userRepository, roleRepository, passwordEncoder, false);

        disabledInitializer.run(null);

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void skipsSeedingByDefaultWhenNoExplicitOptIn() {
        DataInitializer defaultInitializer = new DataInitializer(userRepository, roleRepository, passwordEncoder);

        defaultInitializer.run(null);

        verify(userRepository, never()).save(any(User.class));
    }
}
