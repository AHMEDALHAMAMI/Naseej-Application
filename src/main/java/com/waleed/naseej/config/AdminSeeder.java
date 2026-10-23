package com.waleed.naseej.config;

import com.waleed.naseej.entity.User;
import com.waleed.naseej.enums.AuthProvider;
import com.waleed.naseej.enums.Role;
import com.waleed.naseej.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates the first ADMIN on startup (only if ADMIN_EMAIL is set and the email does not exist yet).
 * Needed because sign-up only allows RESIDENT or INVESTOR, and /users/** is admin-only.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.email:}")
    private String adminEmail;

    @Value("${app.admin.password:}")
    private String adminPassword;

    @Override
    public void run(String... args) {
        if (adminEmail.isBlank() || adminPassword.isBlank()) {
            return;
        }
        String email = adminEmail.trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            return;
        }
        userRepository.save(User.builder()
                .fullName("Naseej Admin")
                .email(email)
                .password(passwordEncoder.encode(adminPassword))
                .authProvider(AuthProvider.LOCAL)
                .role(Role.ADMIN)
                .emailVerified(true)
                .enabled(true)
                .build());
        log.info("Admin account created: {}", email);
    }
}