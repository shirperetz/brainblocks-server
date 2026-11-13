package com.example.brainblocks;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * 2026-11-14 (Shir, before going public): the parent's consent at sign-up, a deletion request with the
 * password, the admin erasing every row of that account, and the public contact form.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "DATABASE_URL=jdbc:h2:mem:privacy;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "DATABASE_USER=sa", "DATABASE_PASSWORD=",
        "JOIN_CODE=4711", "ADMIN_USERNAMES=boss", "PUBLIC_ORIGIN=https://example.org",
        "server.port=0"})
@ActiveProfiles("cloud")
class PrivacyTest {

    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;

    private Map<String, Object> signUp(String user, Map<String, String> extra, int expect) {
        Map<String, String> body = new HashMap<>(Map.of("joinCode", "4711", "username", user, "password", "secret12",
                "name", user, "avatar", "GIRL", "grade", "G3"));
        body.putAll(extra);
        var r = http.postForEntity("/api/learn/auth/register", body, Map.class);
        assertThat(r.getStatusCode().value()).as(user).isEqualTo(expect);
        @SuppressWarnings("unchecked") Map<String, Object> m = r.getBody();
        return m;
    }

    private static HttpHeaders h(String token) {
        HttpHeaders h = new HttpHeaders();
        h.set("X-Learner-Token", token);
        return h;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> call(HttpMethod method, String path, Object body, String token, int expect) {
        var r = http.exchange(path, method, new HttpEntity<>(body, h(token)), Map.class);
        assertThat(r.getStatusCode().value()).as(method + " " + path).isEqualTo(expect);
        return r.getBody();
    }

    @Test
    @SuppressWarnings("unchecked")
    void consentDeletionAndContact() {
        String boss = (String) signUp("boss", Map.of(), 201).get("token");

        // the new form (formMs) without the parent's consent -> refused; with it -> saved with the time
        signUp("noconsent", Map.of("formMs", "9000"), 400);
        Map<String, Object> kid = signUp("kidp", Map.of("formMs", "9000", "parentConsent", "yes"), 201);
        Long kidId = ((Number) ((Map<String, Object>) kid.get("profile")).get("id")).longValue();
        assertThat(jdbc.queryForObject("select consent_at from learn_profile where id = ?", java.sql.Timestamp.class, kidId)).isNotNull();
        // a teacher must accept the privacy policy
        assertThat(http.postForEntity("/api/learn/auth/register-teacher",
                Map.of("username", "teachp", "password", "secret12345", "name", "t", "formMs", "9000"), Map.class)
                .getStatusCode().value()).isEqualTo(400);

        call(HttpMethod.POST, "/api/learn/admin/children/" + kidId + "/approve", null, boss, 200);
        String kt = (String) http.postForObject("/api/learn/auth/login", Map.of("username", "kidp", "password", "secret12"), Map.class).get("token");

        // some of the child's own data, to see it go
        http.exchange("/api/learn/questions?profileId=" + kidId + "&topic=ADDITION&stage=1&count=3&secure=true", HttpMethod.GET,
                new HttpEntity<>(h(kt)), String.class);
        assertThat(jdbc.queryForObject("select count(*) from learn_question where profile_id = ?", Integer.class, kidId)).isPositive();

        // deletion: only with the right password, only for yourself
        call(HttpMethod.POST, "/api/learn/profiles/" + kidId + "/delete-request", Map.of("password", "wrong"), kt, 403);
        call(HttpMethod.POST, "/api/learn/profiles/" + kidId + "/delete-request", Map.of("password", "secret12"), boss, 403);
        Map<String, Object> req = call(HttpMethod.POST, "/api/learn/profiles/" + kidId + "/delete-request", Map.of("password", "secret12"), kt, 200);
        assertThat(req.get("requestedAt")).isNotNull();
        Map<String, Object> admin = call(HttpMethod.GET, "/api/learn/admin/privacy", null, boss, 200);
        assertThat((List<Map<String, Object>>) admin.get("deletionRequests")).extracting(m -> m.get("username")).containsExactly("kidp");
        // the child can't read the admin's list
        call(HttpMethod.GET, "/api/learn/admin/privacy", null, kt, 403);

        // the admin erases: the account and every row that pointed at it
        Map<String, Object> erased = call(HttpMethod.POST, "/api/learn/admin/profiles/" + kidId + "/erase", null, boss, 200);
        assertThat((Map<String, Object>) erased.get("erased")).containsKeys("learn_profile", "learn_question");
        assertThat(jdbc.queryForObject("select count(*) from learn_profile where id = ?", Integer.class, kidId)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from learn_question where profile_id = ?", Integer.class, kidId)).isZero();
        assertThat(http.postForEntity("/api/learn/auth/login", Map.of("username", "kidp", "password", "secret12"), Map.class)
                .getStatusCode().value()).isNotEqualTo(200);

        // the contact form: public, the bot trap applies, the admin sees it
        assertThat(http.postForEntity("/api/learn/auth/contact", Map.of("kind", "accessibility", "name", "הורה",
                "reply", "parent@example.org", "text", "הכפתור של הקול קטן מדי"), Map.class).getStatusCode().value()).isEqualTo(201);
        assertThat(http.postForEntity("/api/learn/auth/contact", Map.of("text", "spam spam", "website", "x"), Map.class)
                .getStatusCode().value()).isEqualTo(400);
        List<Map<String, Object>> messages = (List<Map<String, Object>>) call(HttpMethod.GET, "/api/learn/admin/privacy", null, boss, 200).get("messages");
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0)).containsEntry("kindLabel", "נגישות").containsEntry("done", false);
        call(HttpMethod.POST, "/api/learn/admin/messages/" + messages.get(0).get("id") + "/done", Map.of("done", true), boss, 200);

        // the admin's own account can't be erased
        Long bossId = jdbc.queryForObject("select id from learn_profile where username = 'boss'", Long.class);
        call(HttpMethod.POST, "/api/learn/admin/profiles/" + bossId + "/erase", null, boss, 400);
    }
}
