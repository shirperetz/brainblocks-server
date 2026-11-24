package com.example.brainblocks.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * 2026-11-24 (Shir's iPad stuck on "בודקים את התשובות..."): on PostgreSQL, Hibernate created each enum
 * column with a CHECK listing the values that existed THEN (topic IN ('ADDITION', ...)) and
 * ddl-auto=update never widens it. The topics added on 21.11 (DECIMALS, ...) were refused on save
 * (learn_session_topic_check). The Java enum is the real check, so these list-checks are dropped at
 * start. Only checks of the form "col::text = ANY (ARRAY[...])" (Hibernate's enum checks); nothing on
 * MySQL. Idempotent - once dropped, nothing is found.
 */
@Component
public class EnumCheckCleanup {

    private static final Logger log = LoggerFactory.getLogger(EnumCheckCleanup.class);
    private final DataSource dataSource;

    public EnumCheckCleanup(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void dropEnumListChecks() {
        try (Connection c = dataSource.getConnection()) {
            if (!c.getMetaData().getDatabaseProductName().toLowerCase().contains("postgres")) return;
            List<String[]> found = new ArrayList<>();
            try (Statement s = c.createStatement();
                 ResultSet r = s.executeQuery("select conrelid::regclass::text, conname from pg_constraint "
                         + "where contype = 'c' and connamespace = 'public'::regnamespace "
                         + "and pg_get_constraintdef(oid) like '%= ANY (%ARRAY[%'")) {
                while (r.next()) found.add(new String[] {r.getString(1), r.getString(2)});
            }
            for (String[] f : found) {
                try (Statement s = c.createStatement()) {
                    s.execute("alter table " + f[0] + " drop constraint if exists \"" + f[1].replace("\"", "") + "\"");
                    log.info("dropped enum list check {} on {}", f[1], f[0]);
                }
            }
        } catch (Exception e) {
            log.warn("enum check cleanup skipped: {}", e.getMessage());
        }
    }
}
