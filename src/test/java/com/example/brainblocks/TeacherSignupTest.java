package com.example.brainblocks;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.ActiveProfiles;

/**
 * 2026-11-12 (Shir: "it asks a child or a teacher; a teacher signs up and I approve her; a teacher has
 * a new password for her own children only"): no teachers' code; nothing until the admin's OK; the
 * teacher changes only the password, only of her own child.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "DATABASE_URL=jdbc:h2:mem:teachersignup;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "DATABASE_USER=sa", "DATABASE_PASSWORD=",
        "JOIN_CODE=4711", "ADMIN_USERNAMES=boss", "PUBLIC_ORIGIN=https://example.org",
        "server.port=0"})
@ActiveProfiles("cloud")
class TeacherSignupTest {

    @Autowired
    TestRestTemplate http;

    private HttpHeaders as(String token) {
        HttpHeaders h = new HttpHeaders();
        h.set("X-Learner-Token", token);
        return h;
    }

    private int status(String url, HttpMethod m, String token, Object body) {
        return http.exchange(url, m, new HttpEntity<>(body, token == null ? new HttpHeaders() : as(token)), String.class).getStatusCode().value();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> post(String url, Object body) {
        return http.postForObject(url, body, Map.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void aTeacherSignsUpWithoutACodeAndWaitsForTheAdmin() {
        String boss = (String) post("/api/learn/auth/register", Map.of("joinCode", "4711", "username", "boss", "password", "secret12", "name", "שיר", "avatar", "GIRL", "grade", "G6")).get("token");

        // No code: she is a teacher, waiting - no token, can't log in yet.
        Map<String, Object> t = post("/api/learn/auth/register-teacher", Map.of("username", "dana", "password", "secret1234", "name", "דנה"));
        Map<String, Object> profile = (Map<String, Object>) t.get("profile");
        assertThat(profile).containsEntry("teacher", true).containsEntry("approved", false);
        assertThat(t.get("token")).isNull();
        assertThat(status("/api/learn/auth/login", HttpMethod.POST, null, Map.of("username", "dana", "password", "secret1234"))).isEqualTo(403);
        assertThat(status("/api/learn/auth/register-teacher", HttpMethod.POST, null, Map.of("username", "dana", "password", "secret1234", "name", "שוב"))).isEqualTo(409);

        // The admin approves her; now she logs in and has a classes screen.
        assertThat(status("/api/learn/staff/teachers/" + profile.get("id") + "/approve", HttpMethod.POST, boss, null)).isEqualTo(200);
        String dana = (String) post("/api/learn/auth/login", Map.of("username", "dana", "password", "secret1234")).get("token");
        assertThat(status("/api/learn/staff/me", HttpMethod.GET, dana, null)).isEqualTo(200);

        // Her class, her child; Shir's own child (no class) is not hers.
        Map<String, Object> cls = http.exchange("/api/learn/staff/classes", HttpMethod.POST, new HttpEntity<>(Map.of("name", "ג׳1"), as(dana)), Map.class).getBody();
        Map<String, Object> body = new HashMap<>(Map.of("joinCode", cls.get("joinCode"), "username", "kid", "password", "secret12", "name", "kid", "avatar", "GIRL", "grade", "G3"));
        Object kidId = ((Map<String, Object>) post("/api/learn/auth/register", body).get("profile")).get("id");
        assertThat(status("/api/learn/staff/children/" + kidId + "/approve", HttpMethod.POST, dana, null)).isEqualTo(200);
        Object shirsKid = ((Map<String, Object>) post("/api/learn/auth/register", Map.of("joinCode", "4711", "username", "solo", "password", "secret12", "name", "solo", "avatar", "BOY", "grade", "G2")).get("profile")).get("id");

        // A new password for her child - and only the password (a new username is ignored).
        Map<String, Object> changed = http.exchange("/api/learn/staff/children/" + kidId + "/password", HttpMethod.POST,
                new HttpEntity<>(Map.of("password", "newpass1", "username", "hacked"), as(dana)), Map.class).getBody();
        assertThat(changed).containsEntry("username", "kid");
        assertThat(status("/api/learn/auth/login", HttpMethod.POST, null, Map.of("username", "kid", "password", "newpass1"))).isEqualTo(200);
        // Not for a child that isn't hers.
        assertThat(status("/api/learn/staff/children/" + shirsKid + "/password", HttpMethod.POST, dana, Map.of("password", "newpass1"))).isEqualTo(403);
        // The admin can do both.
        assertThat(http.exchange("/api/learn/staff/children/" + shirsKid + "/password", HttpMethod.POST,
                new HttpEntity<>(Map.of("password", "newpass1", "username", "solo2"), as(boss)), Map.class).getBody()).containsEntry("username", "solo2");
    }
}
