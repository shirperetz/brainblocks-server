package com.example.brainblocks;

import static org.assertj.core.api.Assertions.assertThat;

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
 * 2026-11-21 (Shir: "when the child is really good the exercise gets harder, but not a higher grade's
 * level without the teacher's approval - a feature for the admin and the teacher"): only the child's
 * teacher or the admin opens a topic at a higher grade, per topic; the questions then come from that
 * grade; cancelling brings her back. Another teacher and the child herself can't.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "DATABASE_URL=jdbc:h2:mem:challenge;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "DATABASE_USER=sa", "DATABASE_PASSWORD=",
        "JOIN_CODE=4711", "ADMIN_USERNAMES=boss", "PUBLIC_ORIGIN=https://example.org",
        "server.port=0"})
@ActiveProfiles("cloud")
class ChallengeApprovalTest {

    @Autowired TestRestTemplate http;

    private HttpHeaders as(String token) {
        HttpHeaders h = new HttpHeaders();
        h.set("X-Learner-Token", token);
        return h;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> register(String code, String user) {
        Map<String, Object> body = new java.util.HashMap<>(Map.of("joinCode", code, "username", user,
                "password", "secret12", "name", user, "avatar", "GIRL", "grade", "G3"));
        return http.postForObject("/api/learn/auth/register", body, Map.class);
    }

    @SuppressWarnings("unchecked")
    private Object idOf(Map<String, Object> r) {
        return ((Map<String, Object>) r.get("profile")).get("id");
    }

    private String login(String user) {
        return (String) http.postForObject("/api/learn/auth/login", Map.of("username", user, "password", "secret12"), Map.class).get("token");
    }

    @SuppressWarnings("rawtypes")
    private org.springframework.http.ResponseEntity<Object> send(String url, HttpMethod method, String token, Object body) {
        return http.exchange(url, method, new HttpEntity<>(body, as(token)), Object.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void onlyHerTeacherOrTheAdminOpensAHigherGradePerTopic() {
        String boss = (String) register("4711", "boss").get("token");
        send("/api/learn/staff/teacher-code", HttpMethod.PUT, boss, Map.of("code", "MORIM2026"));
        for (String t : List.of("dana", "rina")) {
            send("/api/learn/staff/teachers/" + idOf(register("MORIM2026", t)) + "/approve", HttpMethod.POST, boss, null);
        }
        String dana = login("dana"), rina = login("rina");
        Map<String, Object> cls = (Map<String, Object>) send("/api/learn/staff/classes", HttpMethod.POST, dana, Map.of("name", "ג׳1")).getBody();
        Object noa = idOf(register((String) cls.get("joinCode"), "noa"));
        send("/api/learn/staff/children/" + noa + "/approve", HttpMethod.POST, dana, null);
        String kid = login("noa");
        String url = "/api/learn/staff/children/" + noa + "/challenge";

        assertThat(send(url, HttpMethod.POST, rina, Map.of("topic", "FRACTIONS", "grade", "G4")).getStatusCode().value()).isIn(403, 404);
        assertThat(send(url, HttpMethod.POST, kid, Map.of("topic", "FRACTIONS", "grade", "G4")).getStatusCode().value()).isIn(401, 403);
        assertThat(send(url, HttpMethod.POST, dana, Map.of("topic", "FRACTIONS", "grade", "G3")).getStatusCode().value()).isEqualTo(400);
        assertThat(send(url, HttpMethod.POST, dana, Map.of("topic", "PERCENT", "grade", "G4")).getStatusCode().value()).isEqualTo(400);
        assertThat(send(url, HttpMethod.POST, dana, Map.of("topic", "DRAGON", "grade", "G4")).getStatusCode().value()).isEqualTo(400);

        // before: grade ג׳ fractions = writing a fraction / the shaded part only
        java.util.Set<Object> before = skills(kid, noa);
        assertThat(before).allMatch(s -> s.equals("FRAC_WRITE") || s.equals("FRAC_SHADED"));

        var ok = send(url, HttpMethod.POST, dana, Map.of("topic", "FRACTIONS", "grade", "G4"));
        assertThat(ok.getStatusCode().value()).isEqualTo(200);
        assertThat((Map<String, Object>) ((Map<String, Object>) ok.getBody()).get("advanced")).containsEntry("FRACTIONS", "G4");
        // after: ד׳ material in fractions (equal fractions, same denominator...)
        assertThat(skills(kid, noa)).anyMatch(s -> !s.equals("FRAC_WRITE") && !s.equals("FRAC_SHADED"));
        // other topics stay at her grade
        Map<String, Object> report = (Map<String, Object>) send("/api/learn/staff/children/" + noa + "/report", HttpMethod.GET, dana, null).getBody();
        assertThat((Map<String, Object>) report.get("advanced")).containsOnlyKeys("FRACTIONS");
        assertThat(report).containsKey("challengeReady");

        // the admin cancels -> back to ג׳
        var cancel = send(url, HttpMethod.POST, boss, java.util.Collections.singletonMap("topic", "FRACTIONS"));
        assertThat(cancel.getStatusCode().value()).isEqualTo(200);
        assertThat((Map<String, Object>) ((Map<String, Object>) cancel.getBody()).get("advanced")).isEmpty();
        assertThat(skills(kid, noa)).allMatch(s -> s.equals("FRAC_WRITE") || s.equals("FRAC_SHADED"));
    }

    @SuppressWarnings("unchecked")
    private java.util.Set<Object> skills(String kid, Object id) {
        java.util.Set<Object> out = new java.util.HashSet<>();
        for (int k = 0; k < 4; k++) {
            List<Map<String, Object>> qs = (List<Map<String, Object>>) send("/api/learn/questions?profileId=" + id
                    + "&topic=FRACTIONS&stage=6&count=20", HttpMethod.GET, kid, null).getBody();
            qs.forEach(q -> out.add(q.get("skill")));
        }
        return out;
    }
}
