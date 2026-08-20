package com.bajar.saman.config;

import com.bajar.saman.entity.Role;
import com.bajar.saman.entity.User;
import com.bajar.saman.entity.UserRole;
import com.bajar.saman.repository.RoleRepository;
import com.bajar.saman.repository.UserRepository;
import com.bajar.saman.repository.UserRoleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Roadmap Addendum v2, §1.3: replaces the previous "manual SQL to create the
 * first admin" approach with a production-safe, repeatable startup check.
 *
 * CommandLineRunner methods run ONCE, automatically, right after the
 * application context is fully initialized — this is Spring Boot's
 * standard mechanism for "do this one thing at startup," used here instead
 * of a one-off manual script that a real deployment would have no
 * reliable way to guarantee gets run.
 *
 * Idempotent by design: checks "does ANY user hold ADMIN" before doing
 * anything. On every startup after the first successful bootstrap, this
 * check finds an existing admin and does nothing — safe to leave running
 * permanently, never just a one-time dev script that needs to be
 * remembered/removed later.
 */
@Component
public class AdminBootstrapConfig implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapConfig.class);
    private static final String ADMIN_ROLE_NAME = "ADMIN";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;

    // Bootstrap credentials come from environment variables / application
    // properties, NEVER hardcoded — same secrets-handling principle already
    // established for the JWT signing secret. If these aren't set, bootstrap
    // is skipped entirely (fail-safe: no admin gets silently created with a
    // guessable default password) rather than falling back to something
    // insecure.
    @Value("${app.bootstrap.admin-email:}")
    private String bootstrapAdminEmail;

    @Value("${app.bootstrap.admin-password:}")
    private String bootstrapAdminPassword;

    public AdminBootstrapConfig(
            UserRepository userRepository,
            RoleRepository roleRepository,
            UserRoleRepository userRoleRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        Role adminRole = roleRepository.findByName(ADMIN_ROLE_NAME)
                .orElseThrow(() -> new IllegalStateException(
                        "ADMIN role not found — check V1 migration seed data"));

        boolean anyAdminExists = userRoleRepository.findAll().stream()
                .anyMatch(ur -> ur.getRole().getId().equals(adminRole.getId()));

        if (anyAdminExists) {
            log.info("Admin bootstrap: at least one ADMIN already exists, skipping.");
            return;
        }

        if (bootstrapAdminEmail.isBlank() || bootstrapAdminPassword.isBlank()) {
            // Fail-safe, not fail-loud-and-crash: a missing bootstrap config
            // is a valid state (e.g. local dev where the SQL-based first
            // admin already exists from before this mechanism was built) —
            // logging a clear warning is correct; refusing to start the
            // whole application over this would be disproportionate.
            log.warn("Admin bootstrap: no ADMIN exists AND no bootstrap credentials configured " +
                    "(app.bootstrap.admin-email / app.bootstrap.admin-password) — skipping. " +
                    "The application will have NO administrator until one is created manually.");
            return;
        }

        User admin = new User(bootstrapAdminEmail, passwordEncoder.encode(bootstrapAdminPassword));
        admin.setEmailVerified(true); // bootstrap admin is trusted by definition,
        // no verification flow needed for it
        userRepository.save(admin);

        UserRole adminAssignment = new UserRole(admin, adminRole);
        userRoleRepository.save(adminAssignment);

        log.info("Admin bootstrap: created initial ADMIN account for {}", bootstrapAdminEmail);
    }
}