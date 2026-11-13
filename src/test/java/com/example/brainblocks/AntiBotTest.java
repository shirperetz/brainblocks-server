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
import org.springframework.test.context.ActiveProfiles;

/**
 * 2026-11-13 (Shir: "security against break-ins and against bots that sign up / play in a loop"):
 * the hidden trap field, a form filled too fast, at most 30 accounts waiting, and per-child limits on
 * rounds; the admin's "own device" rule.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "DATABASE_URL=jdbc:h2:mem:antibot;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "DATABASE_USER=sa", "DATABASE_PASSWORD=",
        "JOIN_CODE=4711", "ADMIN_USERNAMES=boss", "PUBLIC_ORIGIN=https://example.org",
        "server.port=0"})
@ActiveProfiles("cloud")
class AntiBotTest {

    @Autowired TestRestTemplate http;

    private int signUp(String user, Map<String, String> extra) {
        Map<String, String> body = new HashMap<>(Map.of("joinCode", "4711", "username", user, "password", "secret12",
                "name", user, "avatar", "GIRL", "grade", "G3"));
        body.putAll(extra);
        return http.postForEntity("/api/learn/auth/register", body, Map.class).getStatusCode().value();
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void botsAreStopped() {
        // the admin first (approved at once)
        Map<String, Object> r = http.postForObject("/api/learn/auth/register",
                new HashMap<>(Map.of("joinCode", "4711", "username", "boss", "password", "secret12", "name", "boss", "avatar", "GIRL")), Map.class);
        String bt = (String) r.get("token");
        assertThat(bt).isNotNull();

        // the hidden field filled, or the form sent after 1 second -> refused; a person -> fine
        assertThat(signUp("bot1", Map.of("website", "http://spam"))).isEqualTo(400);
        assertThat(signUp("bot2", Map.of("formMs", "900"))).isEqualTo(400);
        assertThat(signUp("kid1", Map.of("website", "", "formMs", "12000"))).isEqualTo(201);
        assertThat(http.postForEntity("/api/learn/auth/register-teacher",
                Map.of("username", "botteach", "password", "secret12345", "name", "x", "website", "x"), Map.class)
                .getStatusCode().value()).isEqualTo(400);

        // at most 30 accounts waiting for approval: kid1 + 29 more fit, the 31st is refused
        for (int i = 2; i <= 30; i++) assertThat(signUp("kid" + i, Map.of())).as("kid" + i).isEqualTo(201);
        assertThat(signUp("kid31", Map.of())).isEqualTo(429);

        // a child can't submit more than 6 rounds a minute
        Long kid = ((Number) ((Map<String, Object>) http.exchange("/api/learn/admin/children", HttpMethod.GET,
                new HttpEntity<>(headers(bt)), List.class).getBody().stream()
                .filter(m -> "kid1".equals(((Map<String, Object>) m).get("username"))).findFirst().orElseThrow()).get("id")).longValue();
        http.exchange("/api/learn/admin/children/" + kid + "/approve", HttpMethod.POST, new HttpEntity<>(headers(bt)), String.class);
        String kt = (String) http.postForObject("/api/learn/auth/login", Map.of("username", "kid1", "password", "secret12"), Map.class).get("token");
        Map<String, Object> round = Map.of("profileId", kid, "topic", "ADDITION", "stage", 1, "mode", "STAGE", "durationMs", 30000, "answers", List.of());
        int last = 0;
        for (int i = 0; i < 7; i++) {
            last = http.exchange("/api/learn/sessions", HttpMethod.POST, new HttpEntity<>(round, headers(kt)), String.class).getStatusCode().value();
            if (i < 6) assertThat(last).as("round " + i).isNotEqualTo(429);
        }
        assertThat(last).isEqualTo(429);
    }

    private static HttpHeaders headers(String token) {
        HttpHeaders h = new HttpHeaders();
        h.set("X-Learner-Token", token);
        return h;
    }

    @Test
    void ownDevicesAreThisComputerAndTailscale() {
        assertThat(com.example.brainblocks.config.LearnerAuthConfigAccess.ownDevice("127.0.0.1")).isTrue();
        assertThat(com.example.brainblocks.config.LearnerAuthConfigAccess.ownDevice("100.101.102.103")).isTrue();
        assertThat(com.example.brainblocks.config.LearnerAuthConfigAccess.ownDevice("100.128.0.1")).isFalse();
        assertThat(com.example.brainblocks.config.LearnerAuthConfigAccess.ownDevice("85.64.12.3")).isFalse();
        assertThat(com.example.brainblocks.config.LearnerAuthConfigAccess.ownDevice("fd7a:115c:a1e0::1")).isTrue();
        assertThat(com.example.brainblocks.config.LearnerAuthConfigAccess.ownDevice(null)).isFalse();
    }
}
