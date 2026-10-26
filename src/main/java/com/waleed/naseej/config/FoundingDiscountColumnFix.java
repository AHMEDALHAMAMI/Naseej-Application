package com.waleed.naseej.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The founding members' discount was removed from the contract: the investor now sets only the idea owner's
 * benefits. Databases created before still have contracts.founding_member_discount as NOT NULL, and
 * ddl-auto=update doesn't relax it, so a new contract would fail to save.
 * This makes the old column optional once, on startup. Old contracts keep their values as a record.
 */
@Slf4j
@Component
@Order(1) // before DemoDataSeeder (20), which saves contracts
@RequiredArgsConstructor
public class FoundingDiscountColumnFix implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        try {
            List<String> nullable = jdbcTemplate.queryForList(
                    "SELECT IS_NULLABLE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() "
                            + "AND TABLE_NAME = 'contracts' AND COLUMN_NAME = 'founding_member_discount'",
                    String.class);
            if (!nullable.isEmpty() && "NO".equalsIgnoreCase(nullable.get(0))) {
                jdbcTemplate.execute("ALTER TABLE contracts MODIFY COLUMN founding_member_discount DECIMAL(5,2) NULL");
                log.info("contracts.founding_member_discount is now optional (the founding members' discount was removed)");
            }
        } catch (DataAccessException e) {
            log.warn("Could not check the contracts.founding_member_discount column: {}", e.getMessage());
        }
    }
}
