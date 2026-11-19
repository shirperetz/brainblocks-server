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
 * 2026-11-12 (Shir: "limit the child and tell him which grade to practice, not all grades"):
 * a teacher locks a child's grade (or the class's grade, for every child in it, also new ones);
 * the child can't change it and gets no questions from another grade's topics.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "DATABASE_URL=jdbc:h2:mem:gradelock;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "DATABASE_USER=sa", "DATABASE_PASSWORD=",
        "JOIN_CODE=4711", "ADMIN_USERNAMES=boss", "PUBLIC_ORIGIN=https://example.org",
        "server.port=0"})
@ActiveProfiles("cloud")
class GradeLockTest {

    @Autowired
    TestRestTemplate http;

    private HttpHeaders as(String token) {
        HttpHeaders h = new HttpHeaders();
        h.set("X-Learner-Token", token);
        return h;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> register(String code, String user, String grade) {
        Map<String, Object> body = new HashMap<>(Map.of("joinCode", code, "username", user, "password", "secret12",
                "name", user, "avatar", "GIRL"));
        if (grade != null) body.put("grade", grade);
        return http.postForObject("/api/learn/auth/register", body, Map.class);
    }

    @SuppressWarnings("unchecked")
    private String login(String user) {
        return (String) http.postForObject("/api/learn/auth/login", Map.of("username", user, "password", "secret12"), Map.class).get("token");
    }

    private int status(String url, HttpMethod m, String token, Object body) {
        return http.exchange(url, m, new HttpEntity<>(body, as(token)), String.class).getStatusCode().value();
    }

    @Test
    @SuppressWarnings("unchecked")
    void aLockedChildPracticesOnlyItsGrade() {
        String boss = (String) register("4711", "boss", "G6").get("token");
        Map<String, Object> cls = http.exchange("/api/learn/staff/classes", HttpMethod.POST,
                new HttpEntity<>(Map.of("name", "ג׳1", "grade", "G3"), as(boss)), Map.class).getBody();

        // A child joining a class that has a grade gets it, locked - whatever grade it picked.
        Map<String, Object> kid = register((String) cls.get("joinCode"), "kid", "G6");
        Object kidId = ((Map<String, Object>) kid.get("profile")).get("id");
        assertThat(((Map<String, Object>) kid.get("profile")).get("grade")).isEqualTo("G3");
        assertThat(((Map<String, Object>) kid.get("profile")).get("gradeLocked")).isEqualTo(true);
        assertThat(status("/api/learn/staff/children/" + kidId + "/approve", HttpMethod.POST, boss, null)).isEqualTo(200);
        String k = login("kid");

        // The child can't change the grade, and gets questions only from its grade's topics.
        assertThat(status("/api/learn/profiles/" + kidId, HttpMethod.PATCH, k, Map.of("grade", "G6"))).isEqualTo(403);
        assertThat(status("/api/learn/profiles/" + kidId, HttpMethod.PATCH, k, Map.of("companion", "OWL"))).isEqualTo(200);
        // 2026-11-19: the dolphin tours the child saw are kept in the account (any device), bad keys refused.
        assertThat(status("/api/learn/profiles/" + kidId, HttpMethod.PATCH, k, Map.of("guideSeen", "home"))).isEqualTo(200);
        assertThat(status("/api/learn/profiles/" + kidId, HttpMethod.PATCH, k, Map.of("guideSeen", "sea"))).isEqualTo(200);
        assertThat(status("/api/learn/profiles/" + kidId, HttpMethod.PATCH, k, Map.of("guideSeen", "home"))).isEqualTo(200);
        assertThat(status("/api/learn/profiles/" + kidId, HttpMethod.PATCH, k, Map.of("guideSeen", "<b>x"))).isEqualTo(400);
        Map<String, Object> me = http.exchange("/api/learn/profiles/" + kidId, HttpMethod.GET, new HttpEntity<>(as(k)), Map.class).getBody();
        assertThat(me.get("guidesSeen")).isEqualTo(List.of("home", "sea"));
        assertThat(status("/api/learn/profiles/" + kidId, HttpMethod.PATCH, k, Map.of("guidesReset", true))).isEqualTo(200);
        me = http.exchange("/api/learn/profiles/" + kidId, HttpMethod.GET, new HttpEntity<>(as(k)), Map.class).getBody();
        assertThat(me.get("guidesSeen")).isEqualTo(List.of());
        assertThat(status("/api/learn/questions?profileId=" + kidId + "&topic=MULTIPLICATION&stage=1", HttpMethod.GET, k, null)).isEqualTo(200);
        assertThat(status("/api/learn/questions?profileId=" + kidId + "&topic=ANGLE_CALC&stage=1", HttpMethod.GET, k, null)).isEqualTo(403);
        assertThat(status("/api/learn/sessions", HttpMethod.POST, k, Map.of("profileId", kidId, "topic", "ANGLE_CALC", "stage", 1,
                "answers", List.of(Map.of("questionText", "x", "chosenAnswer", "1", "correctAnswer", "1", "correct", true))))).isEqualTo(403);

        // The class moves to grade ה׳ - every child in it too.
        assertThat(status("/api/learn/staff/classes/" + cls.get("id"), HttpMethod.PATCH, boss, Map.of("grade", "G5"))).isEqualTo(200);
        assertThat(status("/api/learn/questions?profileId=" + kidId + "&topic=ANGLE_CALC&stage=1", HttpMethod.GET, k, null)).isEqualTo(200);

        // One child set by hand, then unlocked: the child may choose again.
        assertThat(status("/api/learn/staff/children/" + kidId + "/grade", HttpMethod.POST, boss, Map.of("grade", "G2", "locked", true))).isEqualTo(200);
        assertThat(status("/api/learn/questions?profileId=" + kidId + "&topic=FRACTIONS&stage=1", HttpMethod.GET, k, null)).isEqualTo(403);
        assertThat(status("/api/learn/staff/children/" + kidId + "/grade", HttpMethod.POST, boss, Map.of("locked", false))).isEqualTo(200);
        // 2026-11-12 (Shir): by default the child may change grade; the admin/teacher can turn that off.
        assertThat(status("/api/learn/profiles/" + kidId, HttpMethod.PATCH, k, Map.of("grade", "G4"))).isEqualTo(200);
        assertThat(status("/api/learn/staff/children/" + kidId + "/grade", HttpMethod.POST, boss, Map.of("changeAllowed", false))).isEqualTo(200);
        assertThat(status("/api/learn/profiles/" + kidId, HttpMethod.PATCH, k, Map.of("grade", "G3"))).isEqualTo(403);
        assertThat(status("/api/learn/profiles/" + kidId, HttpMethod.PATCH, k, Map.of("grade", "G4"))).isEqualTo(200); // same grade: fine
        assertThat(status("/api/learn/staff/children/" + kidId + "/grade", HttpMethod.POST, boss, Map.of("changeAllowed", true))).isEqualTo(200);
        assertThat(status("/api/learn/profiles/" + kidId, HttpMethod.PATCH, k, Map.of("grade", "G3"))).isEqualTo(200);

        // A child can't lock or set itself through the staff path.
        assertThat(status("/api/learn/staff/children/" + kidId + "/grade", HttpMethod.POST, k, Map.of("grade", "G6"))).isEqualTo(403);
    }
}
