package com.example.brainblocks;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
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
 * 2026-11-12 (idea #5): a teacher gives her class "3 rounds of fractions this week". The children of
 * that class see it with their progress; only STAGE rounds of that topic with at least half right
 * count; another teacher can't see or set it; a cancelled task is gone for the children.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "DATABASE_URL=jdbc:h2:mem:weekly;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "DATABASE_USER=sa", "DATABASE_PASSWORD=",
        "JOIN_CODE=4711", "ADMIN_USERNAMES=boss", "PUBLIC_ORIGIN=https://example.org",
        "server.port=0"})
@ActiveProfiles("cloud")
class WeeklyAssignmentTest {

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

    @SuppressWarnings("unchecked")
    private <T> T call(String url, HttpMethod m, String token, Object body, Class<T> type) {
        return http.exchange(url, m, new HttpEntity<>(body, as(token)), type).getBody();
    }

    /** A finished round of 10 questions, `right` of them right. */
    private int round(String token, Object kidId, String topic, String mode, int right) {
        List<Map<String, Object>> answers = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            boolean ok = i < right;
            answers.add(Map.of("questionText", "2 + " + i, "chosenAnswer", ok ? "1" : "2", "correctAnswer", "1", "correct", ok));
        }
        return status("/api/learn/sessions", HttpMethod.POST, token,
                Map.of("profileId", kidId, "topic", topic, "stage", 1, "mode", mode, "answers", answers));
    }

    @Test
    @SuppressWarnings("unchecked")
    void aWeeklyTaskCountsTheClassRealRounds() {
        String boss = (String) register("4711", "boss", "G6").get("token");
        assertThat(status("/api/learn/staff/teacher-code", HttpMethod.PUT, boss, Map.of("code", "MORIM2026"))).isEqualTo(200);
        for (String t : List.of("dana", "rina")) {
            Object id = ((Map<String, Object>) register("MORIM2026", t, null).get("profile")).get("id");
            assertThat(status("/api/learn/staff/teachers/" + id + "/approve", HttpMethod.POST, boss, null)).isEqualTo(200);
        }
        String dana = login("dana");
        String rina = login("rina");
        Map<String, Object> cls = call("/api/learn/staff/classes", HttpMethod.POST, dana, Map.of("name", "ה׳1", "grade", "G5"), Map.class);
        Object classId = cls.get("id");
        String base = "/api/learn/staff/classes/" + classId + "/assignments";

        Map<String, Object> kid = register((String) cls.get("joinCode"), "kid", "G5");
        Object kidId = ((Map<String, Object>) kid.get("profile")).get("id");
        assertThat(status("/api/learn/staff/children/" + kidId + "/approve", HttpMethod.POST, dana, null)).isEqualTo(200);
        String k = login("kid");

        // Bad input and other teachers are refused; the island games don't belong to grade ה׳.
        assertThat(status(base, HttpMethod.POST, dana, Map.of("topic", "NOPE"))).isEqualTo(400);
        assertThat(status(base, HttpMethod.POST, dana, Map.of("topic", "FRACTIONS", "rounds", 0))).isEqualTo(400);
        assertThat(status(base, HttpMethod.POST, dana, Map.of("topic", "FRACTIONS", "rounds", 11))).isEqualTo(400);
        assertThat(status(base, HttpMethod.POST, dana, Map.of("topic", "DRAGON"))).isEqualTo(400);
        assertThat(status(base, HttpMethod.POST, rina, Map.of("topic", "FRACTIONS"))).isEqualTo(403);
        assertThat(status(base, HttpMethod.GET, rina, null)).isEqualTo(403);
        assertThat(status(base, HttpMethod.GET, k, null)).isEqualTo(403);
        assertThat(status(base, HttpMethod.POST, k, Map.of("topic", "FRACTIONS"))).isEqualTo(403);

        // A good fractions round this morning, before the task exists - must not count (the bot found it did).
        assertThat(round(k, kidId, "FRACTIONS", "STAGE", 10)).isEqualTo(200);

        // Dana: 3 rounds of fractions this week.
        Map<String, Object> task = call(base, HttpMethod.POST, dana, Map.of("topic", "FRACTIONS", "rounds", 3), Map.class);
        assertThat(task.get("rounds")).isEqualTo(3);

        List<Map<String, Object>> mine = call("/api/learn/profiles/" + kidId + "/assignments", HttpMethod.GET, k, null, List.class);
        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).get("done")).isEqualTo(0);
        assertThat(mine.get(0).get("daysLeft")).isEqualTo(7);

        // What counts: fraction STAGE rounds, half right or better. Not: another topic, the daily, a 3/10 round.
        assertThat(round(k, kidId, "FRACTIONS", "STAGE", 8)).isEqualTo(200);
        assertThat(round(k, kidId, "FRACTIONS", "STAGE", 5)).isEqualTo(200);
        assertThat(round(k, kidId, "FRACTIONS", "STAGE", 3)).isEqualTo(200);
        assertThat(round(k, kidId, "ANGLES", "STAGE", 10)).isEqualTo(200);
        assertThat(round(k, kidId, "FRACTIONS", "TIMED", 10)).isEqualTo(200);
        mine = call("/api/learn/profiles/" + kidId + "/assignments", HttpMethod.GET, k, null, List.class);
        assertThat(mine.get(0).get("done")).isEqualTo(2);

        // The third good round finishes it; more rounds don't go past the target.
        assertThat(round(k, kidId, "FRACTIONS", "STAGE", 9)).isEqualTo(200);
        assertThat(round(k, kidId, "FRACTIONS", "STAGE", 9)).isEqualTo(200);
        mine = call("/api/learn/profiles/" + kidId + "/assignments", HttpMethod.GET, k, null, List.class);
        assertThat(mine.get(0).get("done")).isEqualTo(3);

        // Dana sees who finished; the admin sees it too.
        List<Map<String, Object>> view = call(base, HttpMethod.GET, dana, null, List.class);
        assertThat(view).hasSize(1);
        assertThat(view.get(0).get("finished")).isEqualTo(1);
        assertThat(view.get(0).get("total")).isEqualTo(1);
        assertThat(((List<Map<String, Object>>) view.get(0).get("children")).get(0).get("done")).isEqualTo(3);
        assertThat(status(base, HttpMethod.GET, boss, null)).isEqualTo(200);

        // A child cannot read another child's tasks (looks like no such profile, as everywhere).
        Map<String, Object> other = register((String) cls.get("joinCode"), "kid2", "G5");
        Object otherId = ((Map<String, Object>) other.get("profile")).get("id");
        assertThat(status("/api/learn/staff/children/" + otherId + "/approve", HttpMethod.POST, dana, null)).isEqualTo(200);
        assertThat(status("/api/learn/profiles/" + otherId + "/assignments", HttpMethod.GET, k, null)).isEqualTo(404);

        // Idea #6 - the class building: 2 children -> 60 right answers; kid's ~60 count, kid2 helped with none.
        Map<String, Object> b = call("/api/learn/profiles/" + kidId + "/class-building", HttpMethod.GET, k, null, Map.class);
        long mineRight = ((Number) b.get("mine")).longValue();
        assertThat(mineRight).isGreaterThan(40);
        assertThat(b).containsEntry("target", 60).containsEntry("children", 2).containsEntry("helped", 1)
                .containsEntry("className", "ה׳1");
        assertThat(((Number) b.get("built")).longValue()).isEqualTo(Math.min(mineRight, 60));
        Map<String, Object> tb = call("/api/learn/staff/classes/" + classId + "/building", HttpMethod.GET, dana, null, Map.class);
        assertThat(tb.get("built")).isEqualTo(b.get("built"));
        assertThat(tb).doesNotContainKey("mine");
        assertThat(status("/api/learn/staff/classes/" + classId + "/building", HttpMethod.GET, rina, null)).isEqualTo(403);
        assertThat(status("/api/learn/staff/classes/" + classId + "/building", HttpMethod.GET, k, null)).isEqualTo(403);
        assertThat(status("/api/learn/profiles/" + otherId + "/class-building", HttpMethod.GET, k, null)).isEqualTo(404);

        // At most 3 running tasks.
        assertThat(status(base, HttpMethod.POST, dana, Map.of("topic", "AREA", "rounds", 2, "days", 5))).isEqualTo(200);
        assertThat(status(base, HttpMethod.POST, dana, Map.of("topic", "ANGLES"))).isEqualTo(200);
        assertThat(status(base, HttpMethod.POST, dana, Map.of("topic", "PERIMETER"))).isEqualTo(400);

        // Rina can't cancel Dana's task; Dana can, and the child no longer sees it.
        Object taskId = task.get("id");
        assertThat(status("/api/learn/staff/assignments/" + taskId + "/cancel", HttpMethod.POST, rina, null)).isEqualTo(403);
        assertThat(status("/api/learn/staff/assignments/" + taskId + "/cancel", HttpMethod.POST, dana, null)).isEqualTo(200);
        mine = call("/api/learn/profiles/" + kidId + "/assignments", HttpMethod.GET, k, null, List.class);
        assertThat(mine).hasSize(2).noneMatch(m -> m.get("topic").equals("FRACTIONS"));

        // kid2 sees the class's tasks; moved out of the class (admin) - none.
        // (Moved, not a new sign-up: 5 sign-ups an hour per visitor.)
        String k2 = login("kid2");
        assertThat(call("/api/learn/profiles/" + otherId + "/assignments", HttpMethod.GET, k2, null, List.class)).hasSize(2);
        assertThat(status("/api/learn/staff/children/" + otherId + "/class", HttpMethod.POST, boss, Map.of("classId", 0))).isEqualTo(200);
        assertThat(call("/api/learn/profiles/" + otherId + "/assignments", HttpMethod.GET, k2, null, List.class)).isEmpty();
        assertThat(call("/api/learn/profiles/" + otherId + "/class-building", HttpMethod.GET, k2, null, Map.class)).isEmpty();
    }
}
