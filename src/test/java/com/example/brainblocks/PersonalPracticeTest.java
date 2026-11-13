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
 * 2026-11-13 (Shir: "practice from the report"): the teacher gives ONE child a short task on what she
 * gets wrong; only that child sees it, not the class list; another teacher can't; no duplicates.
 * Also: a class can sign up from one school address (the limit was 5 an hour).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "DATABASE_URL=jdbc:h2:mem:practice;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "DATABASE_USER=sa", "DATABASE_PASSWORD=",
        "JOIN_CODE=4711", "ADMIN_USERNAMES=boss", "PUBLIC_ORIGIN=https://example.org",
        "server.port=0"})
@ActiveProfiles("cloud")
class PersonalPracticeTest {

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
    void aTaskForOneChildFromTheReport() {
        String boss = (String) register("4711", "boss").get("token");
        assertThat(send("/api/learn/staff/teacher-code", HttpMethod.PUT, boss, Map.of("code", "MORIM2026")).getStatusCode().value()).isEqualTo(200);
        for (String t : List.of("dana", "rina")) {
            assertThat(send("/api/learn/staff/teachers/" + idOf(register("MORIM2026", t)) + "/approve", HttpMethod.POST, boss, null)
                    .getStatusCode().value()).isEqualTo(200);
        }
        String dana = login("dana"), rina = login("rina");
        Map<String, Object> cls = (Map<String, Object>) send("/api/learn/staff/classes", HttpMethod.POST, dana, Map.of("name", "ג׳1")).getBody();
        String code = (String) cls.get("joinCode");

        // a whole class signs up from one address: 8 children in a row (was blocked after 5)
        Object noa = null, tal = null;
        for (int i = 0; i < 8; i++) {
            Map<String, Object> r = register(code, "kid" + i);
            assertThat(r).as("sign-up " + i).containsKey("profile");
            Object id = idOf(r);
            assertThat(send("/api/learn/staff/children/" + id + "/approve", HttpMethod.POST, dana, null).getStatusCode().value()).isEqualTo(200);
            if (i == 0) noa = id;
            if (i == 1) tal = id;
        }

        String url = "/api/learn/staff/children/" + noa + "/practice";
        assertThat(send(url, HttpMethod.POST, rina, Map.of("topic", "ADDITION")).getStatusCode().value()).isIn(403, 404);
        var made = send(url, HttpMethod.POST, dana, Map.of("topic", "ADDITION"));
        assertThat(made.getStatusCode().value()).isEqualTo(200);
        assertThat((Map<String, Object>) made.getBody()).containsEntry("rounds", 2).containsEntry("done", 0);
        assertThat(send(url, HttpMethod.POST, dana, Map.of("topic", "ADDITION")).getStatusCode().value()).isEqualTo(409);
        assertThat(send(url, HttpMethod.POST, dana, Map.of("topic", "NOPE")).getStatusCode().value()).isEqualTo(400);

        // noa sees it, tal doesn't, the class's task list doesn't
        List<Map<String, Object>> mine = (List<Map<String, Object>>) send("/api/learn/profiles/" + noa + "/assignments", HttpMethod.GET, login("kid0"), null).getBody();
        assertThat(mine).extracting(m -> m.get("topic")).containsExactly("ADDITION");
        assertThat((List<?>) send("/api/learn/profiles/" + tal + "/assignments", HttpMethod.GET, login("kid1"), null).getBody()).isEmpty();
        assertThat((List<?>) send("/api/learn/staff/classes/" + cls.get("id") + "/assignments", HttpMethod.GET, dana, null).getBody()).isEmpty();
        // the report's list, and cancelling it
        List<Map<String, Object>> list = (List<Map<String, Object>>) send(url, HttpMethod.GET, dana, null).getBody();
        assertThat(list).hasSize(1);
        assertThat(send("/api/learn/staff/assignments/" + list.get(0).get("id") + "/cancel", HttpMethod.POST, rina, null).getStatusCode().value()).isIn(403, 404);
        assertThat(send("/api/learn/staff/assignments/" + list.get(0).get("id") + "/cancel", HttpMethod.POST, dana, null).getStatusCode().value()).isEqualTo(200);
        assertThat((List<?>) send("/api/learn/profiles/" + noa + "/assignments", HttpMethod.GET, login("kid0"), null).getBody()).isEmpty();

        // a child who already has an account (no class) asks to join with the class code
        Map<String, Object> r = register("4711", "freekid");
        Object free = idOf(r);
        String fk = (String) r.get("token");
        if (fk == null) {
            send("/api/learn/staff/children/" + free + "/approve", HttpMethod.POST, boss, null);
            fk = login("freekid");
        }
        String join = "/api/learn/profiles/" + free + "/join-class";
        assertThat(send(join, HttpMethod.POST, fk, Map.of("code", "NOPE00")).getStatusCode().value()).isEqualTo(404);
        Map<String, Object> st = (Map<String, Object>) send(join, HttpMethod.POST, fk, Map.of("code", code.toLowerCase())).getBody();
        assertThat(st).containsEntry("pendingClassName", "ג׳1").containsEntry("pendingTeacher", "dana");
        assertThat(send(join, HttpMethod.POST, login("kid2"), Map.of("code", code)).getStatusCode().value()).isEqualTo(404); // not hers
        // rina doesn't see it, dana does; rina can't accept
        assertThat((List<?>) send("/api/learn/staff/class-requests", HttpMethod.GET, rina, null).getBody()).isEmpty();
        assertThat((List<Map<String, Object>>) send("/api/learn/staff/class-requests", HttpMethod.GET, dana, null).getBody())
                .extracting(m -> m.get("name")).containsExactly("freekid");
        assertThat(send("/api/learn/staff/class-requests/" + free + "/accept", HttpMethod.POST, rina, null).getStatusCode().value()).isEqualTo(403);
        // the class is full at 8 -> dana can't accept until the admin raises it
        send("/api/learn/staff/classes/" + cls.get("id"), HttpMethod.PATCH, boss, Map.of("maxStudents", 8));
        assertThat(send("/api/learn/staff/class-requests/" + free + "/accept", HttpMethod.POST, dana, null).getStatusCode().value()).isEqualTo(409);
        send("/api/learn/staff/classes/" + cls.get("id"), HttpMethod.PATCH, boss, Map.of("maxStudents", 9));
        assertThat(send("/api/learn/staff/class-requests/" + free + "/accept", HttpMethod.POST, dana, null).getStatusCode().value()).isEqualTo(200);
        st = (Map<String, Object>) send(join, HttpMethod.GET, fk, null).getBody();
        assertThat(st).containsEntry("className", "ג׳1").containsEntry("pendingClassName", null);
        // now dana sees her report
        assertThat(send("/api/learn/staff/children/" + free + "/report", HttpMethod.GET, dana, null).getStatusCode().value()).isEqualTo(200);
    }
}
