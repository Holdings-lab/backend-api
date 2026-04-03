package com.project.server.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LegacyUserEmailMigrationRunner implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            // Legacy schema compatibility: username -> email migration.
            jdbcTemplate.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS email VARCHAR(100)");
            jdbcTemplate.execute("UPDATE users SET email = username WHERE email IS NULL AND username IS NOT NULL");

            jdbcTemplate.execute("""
                    DO $$
                    BEGIN
                        IF EXISTS (
                            SELECT 1
                            FROM information_schema.table_constraints
                            WHERE table_name = 'users'
                              AND constraint_name = 'uk_users_email'
                        ) THEN
                            ALTER TABLE users DROP CONSTRAINT uk_users_email;
                        END IF;
                    END
                    $$
                    """);

            jdbcTemplate.execute("ALTER TABLE users ALTER COLUMN email SET NOT NULL");
            jdbcTemplate.execute("ALTER TABLE users ADD CONSTRAINT uk_users_email UNIQUE (email)");
            log.info("Legacy users.username -> users.email migration checked/applied");
        } catch (Exception exception) {
            // If table does not exist yet, Hibernate DDL will create it and this can be ignored.
            log.warn("Legacy email migration skipped or partially applied: {}", exception.getMessage());
        }
    }
}
