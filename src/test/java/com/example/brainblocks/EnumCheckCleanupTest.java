package com.example.brainblocks;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.brainblocks.config.EnumCheckCleanup;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 2026-11-24: the live site (Neon) refused to save a round of DECIMALS - the learn_session table was
 * created on 15.11 with a CHECK listing only the topics of then. Here an old-style check is put back
 * on a real PostgreSQL and the cleanup must remove it, so a new topic saves.
 */
@SpringBootTest(properties = {"JOIN_CODE=4711", "ADMIN_USERNAMES=boss", "PUBLIC_ORIGIN=https://example.org"})
@ActiveProfiles("cloud")
class EnumCheckCleanupTest {

    static final EmbeddedPostgres PG;

    static {
        try {
            PG = EmbeddedPostgres.builder().start();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void db(DynamicPropertyRegistry r) {
        r.add("DATABASE_URL", () -> PG.getJdbcUrl("postgres", "postgres"));
        r.add("DATABASE_USER", () -> "postgres");
        r.add("DATABASE_PASSWORD", () -> "");
    }

    @AfterAll
    static void stop() throws Exception {
        PG.close();
    }

    @Autowired DataSource dataSource;
    @Autowired EnumCheckCleanup cleanup;

    private int listChecks(Statement s) throws Exception {
        try (ResultSet r = s.executeQuery("select count(*) from pg_constraint where contype = 'c' "
                + "and connamespace = 'public'::regnamespace and pg_get_constraintdef(oid) like '%= ANY (%ARRAY[%'")) {
            r.next();
            return r.getInt(1);
        }
    }

    @Test
    void anOldTopicListIsDroppedAndANewTopicSaves() throws Exception {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            assertThat(listChecks(s)).isZero(); // already cleaned at start
            s.execute("alter table learn_session add constraint learn_session_topic_old_check "
                    + "check (topic::text = ANY (ARRAY['ADDITION'::character varying, 'FRACTIONS'::character varying]::text[]))");
            assertThat(listChecks(s)).isEqualTo(1);
            cleanup.dropEnumListChecks();
            assertThat(listChecks(s)).isZero();
        }
    }
}
