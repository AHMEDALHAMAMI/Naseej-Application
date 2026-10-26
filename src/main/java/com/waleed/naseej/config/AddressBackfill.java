package com.waleed.naseej.config;

import com.waleed.naseej.entity.User;
import com.waleed.naseej.enums.Role;
import com.waleed.naseej.repository.UserRepository;
import com.waleed.naseej.service.AddressService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * One-off startup step for accounts from before saved addresses existed: every resident without an address gets a
 * primary one from their saved point (or their neighborhood center, flagged to confirm). Runs after the demo data.
 * Safe to run on every start: residents who already have an address are skipped, and nothing existing is changed.
 */
@Slf4j
@Component
@Order(30)
@RequiredArgsConstructor
public class AddressBackfill implements CommandLineRunner {

    private final UserRepository userRepository;
    private final AddressService addressService;

    @Override
    public void run(String... args) {
        int created = 0;
        for (User resident : userRepository.findByRoleOrderByIdDesc(Role.RESIDENT)) {
            try {
                if (addressService.backfill(resident) != null) {
                    created++;
                }
            } catch (RuntimeException e) {
                log.warn("Could not create a home address for user {}: {}", resident.getId(), e.getMessage());
            }
        }
        if (created > 0) {
            log.info("Created home addresses for {} existing residents", created);
        }
    }
}
