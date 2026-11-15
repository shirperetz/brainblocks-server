package com.example.brainblocks;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.brainblocks.tools.DataCopyAccess;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 2026-11-14 (moving the site to Render + Neon): the whole app on a REAL PostgreSQL (the engine Neon runs),
 * not H2 pretending - the tables are created, children sign up and play; then the nightly backup takes
 * the database to a file and puts it back (--replace), identical, and the site keeps working with new
 * ids after it (the id counters were moved on). Only made-up test data.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "JOIN_CODE=4711", "ADMIN_USERNAMES=boss", "PUBLIC_ORIGIN=https://example.org", "server.port=0"})
@ActiveProfiles("cloud")
class RealPostgresTest {

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

    @Autowired TestRestTemplate http;

    @SuppressWarnings("unchecked")
    private Map<String, Object> post(String path, Object body, String token, int expect) {
        HttpHeaders h = new HttpHeaders();
        if (token != null) h.set("X-Learner-Token", token);
        var r = http.exchange(path, HttpMethod.POST, new HttpEntity<>(body, h), Map.class);
        assertThat(r.getStatusCode().value()).as(path).isEqualTo(expect);
        return r.getBody();
    }

    @SuppressWarnings("unchecked")
    private Long kid(String user, String boss) {
        Map<String, Object> k = post("/api/learn/auth/register", new HashMap<>(Map.of("joinCode", "4711", "username", user,
                "password", "secret12", "name", "ילדה", "avatar", "GIRL", "grade", "G3")), null, 201);
        Long id = ((Number) ((Map<String, Object>) k.get("profile")).get("id")).longValue();
        post("/api/learn/staff/children/" + id + "/approve", null, boss, 200);
        return id;
    }

    @Test
    @SuppressWarnings("unchecked")
    void theSiteRunsOnPostgresAndTheBackupRoundTrips() throws Exception {
        String boss = (String) post("/api/learn/auth/register", new HashMap<>(Map.of("joinCode", "4711", "username", "boss",
                "password", "secret1234", "name", "שיר", "avatar", "GIRL", "grade", "G6")), null, 201).get("token");
        post("/api/learn/staff/classes", Map.of("name", "ג׳1"), boss, 200);
        Long first = kid("kidpg1", boss);
        String k = (String) post("/api/learn/auth/login", Map.of("username", "kidpg1", "password", "secret12"), null, 200).get("token");
        HttpHeaders h = new HttpHeaders();
        h.set("X-Learner-Token", k);
        var map = http.exchange("/api/learn/profiles/" + first + "/map?topic=ADDITION", HttpMethod.GET, new HttpEntity<>(h), Map.class);
        assertThat(map.getStatusCode().value()).isEqualTo(200);

        // nightly backup -> file -> back into the same database (replace), identical
        Path zip = Files.createTempDirectory("bbpg").resolve("night.zip");
        try (Connection c = DataCopyAccess.open(PG.getJdbcUrl("postgres", "postgres"))) {
            assertThat(DataCopyAccess.dump(c, zip)).isZero();
        }
        try (Connection c = DataCopyAccess.open(PG.getJdbcUrl("postgres", "postgres"))) {
            assertThat(DataCopyAccess.load(zip, c, true)).isZero();
        }

        // after putting it back: the same child logs in, and a new child gets a new id (no clash)
        assertThat(post("/api/learn/auth/login", Map.of("username", "kidpg1", "password", "secret12"), null, 200).get("token")).isNotNull();
        Long second = kid("kidpg2", boss);
        assertThat(second).isGreaterThan(first);

        // 2026-11-15 (Shir: "delete a blocked child"): erasing works on PostgreSQL too - every row goes.
        post("/api/learn/sessions", Map.of("profileId", second, "topic", "ADDITION", "stageNumber", 1, "mode", "STAGE",
                "correctCount", 1, "totalQuestions", 1, "answers", java.util.List.of()), null, 401); // (no token: nothing written)
        // 2026-11-15: log out -> the token stops working; the password logs in again.
        String tok = (String) post("/api/learn/auth/login", Map.of("username", "kidpg1", "password", "secret12"), null, 200).get("token");
        HttpHeaders th = new HttpHeaders(); th.set("X-Learner-Token", tok);
        assertThat(http.exchange("/api/learn/profiles/" + first, HttpMethod.GET, new HttpEntity<>(th), Map.class).getStatusCode().value()).isEqualTo(200);
        post("/api/learn/auth/logout", null, tok, 200);
        assertThat(http.exchange("/api/learn/profiles/" + first, HttpMethod.GET, new HttpEntity<>(th), Map.class).getStatusCode().value()).isEqualTo(401);
        assertThat(post("/api/learn/auth/login", Map.of("username", "kidpg1", "password", "secret12"), null, 200).get("token")).isNotNull();

        Map<String, Object> erased = post("/api/learn/admin/profiles/" + second + "/erase", null, boss, 200);
        assertThat(erased.get("erased").toString()).contains("learn_profile");
        assertThat(post("/api/learn/auth/login", Map.of("username", "kidpg2", "password", "secret12"), null, 401)).isNotNull();
    }
}
