package com.geoshield.identity.service;

import com.geoshield.identity.entity.Role;
import com.geoshield.identity.entity.UserRole;
import com.geoshield.identity.repository.RoleRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class RoleInitializer implements ApplicationRunner {
    private final RoleRepository roleRepository;

    RoleInitializer(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (Role role : Role.values()) {
            if (roleRepository.findByName(role).isEmpty()) {
                roleRepository.save(new UserRole(role));
            }
        }
    }
}
