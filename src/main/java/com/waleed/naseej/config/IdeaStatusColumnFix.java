package com.waleed.naseej.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Databases created before IdeaStatus.REJECTED, HIDDEN or DRAFT existed have ideas.status as an ENUM
 * without them, and ddl-auto=update doesn't widen it, so saving such an idea fails with
 * "Data truncated for column 'status'".
 * This turns the column into VARCHAR once, on startup, when one of them is missing.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdeaStatusColumnFix implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        try {
            List<String> types = jdbcTemplate.queryForList(
                    "SELECT COLUMN_TYPE FROM information_schema.COLUMNS "
                            + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ideas' AND COLUMN_NAME = 'status'",
                    String.class);
            if (types.isEmpty()) {
                return;
            }
            String type = types.get(0).toLowerCase();
            if (type.startsWith("enum")
                    && !(type.contains("'rejected'") && type.contains("'hidden'") && type.contains("'draft'"))) {
                jdbcTemplate.execute("ALTER TABLE ideas MODIFY COLUMN status VARCHAR(20) NOT NULL");
                log.info("ideas.status changed from {} to VARCHAR(20) so drafts, rejected and hidden ideas can be saved", type);
            }
        } catch (DataAccessException e) {
            log.warn("Could not check the ideas.status column: {}", e.getMessage());
        }
    }
}
