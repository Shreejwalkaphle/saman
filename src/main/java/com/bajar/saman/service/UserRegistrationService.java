package com.bajar.saman.service;

import com.bajar.saman.entity.Role;
import com.bajar.saman.entity.User;
import com.bajar.saman.entity.UserRole;
import com.bajar.saman.exception.DuplicateEmailException;
import com.bajar.saman.repository.RoleRepository;
import com.bajar.saman.repository.UserRepository;
import com.bajar.saman.repository.UserRoleRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserRegistrationService {

    private static final String DEFAULT_ROLE = "CUSTOMER";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;

    public UserRegistrationService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            UserRoleRepository userRoleRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User register(String email, String rawPassword) {

        if (userRepository.existsByEmail(email)) {
            throw new DuplicateEmailException(email);
        }

        String hashedPassword = passwordEncoder.encode(rawPassword);
        User user = new User(email, hashedPassword);
        try {
            userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            // Race condition safety net: another concurrent request beat us
            // to it between the existsByEmail check above and this save.
            throw new DuplicateEmailException(email);
        }

        Role customerRole = roleRepository.findByName(DEFAULT_ROLE)
                .orElseThrow(() -> new IllegalStateException(
                        "Default role '" + DEFAULT_ROLE + "' not found — check V1 migration seed data"));

        UserRole userRole = new UserRole(user, customerRole);
        userRoleRepository.save(userRole);

        return user;
    }
}