package com.geoshield.identity.service;

import com.geoshield.identity.entity.Role;
import com.geoshield.identity.entity.User;
import com.geoshield.identity.entity.UserRole;
import com.geoshield.identity.repository.RoleRepository;
import com.geoshield.identity.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bootstrap data initializer for development and testing environments.
 * Seeds default administrator and emergency responder accounts if none exist in the database.
 * NOTE: These are DEVELOPMENT / DEMO credentials for academic review and demonstration.
 */
@Service
@Order(10)
public class DataInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserRepository userRepository, RoleRepository roleRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedDefaultAdmin();
        seedDefaultResponder();
    }

    private void seedDefaultAdmin() {
        if (userRepository.countByRoleName(Role.ADMIN) == 0 && !userRepository.existsByEmail("admin@geoshield.com")) {
            UserRole adminRole = roleRepository.findByName(Role.ADMIN).orElse(null);
            if (adminRole != null) {
                User admin = new User(
                        "admin",
                        "admin@geoshield.com",
                        passwordEncoder.encode("Admin@123456"),
                        "System Administrator",
                        "+919876543210",
                        adminRole
                );
                userRepository.save(admin);
                log.info("[DEVELOPMENT/DEMO] Seeded default Administrator: admin@geoshield.com / Admin@123456");
            }
        }
    }

    private void seedDefaultResponder() {
        if (userRepository.countByRoleName(Role.RESPONDER) == 0 && !userRepository.existsByEmail("responder@geoshield.com")) {
            UserRole responderRole = roleRepository.findByName(Role.RESPONDER).orElse(null);
            if (responderRole != null) {
                User responder = new User(
                        "responder",
                        "responder@geoshield.com",
                        passwordEncoder.encode("Responder@123456"),
                        "Emergency Responder Unit",
                        "+919876543211",
                        responderRole
                );
                userRepository.save(responder);
                log.info("[DEVELOPMENT/DEMO] Seeded default Emergency Responder: responder@geoshield.com / Responder@123456");
            }
        }
    }
}
