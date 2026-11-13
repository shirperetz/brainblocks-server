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
 * 2026-11-13 (Shir: "I, the admin, set a number for a class - with 25 she can approve up to 25, never
 * more"): only the admin sets the limit; at the limit the teacher (and the admin) can't approve or
 * move one more child in; waiting children stay waiting; raising the limit lets them in.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "DATABASE_URL=jdbc:h2:mem:classlimit;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "DATABASE_USER=sa", "DATABASE_PASSWORD=",
        "JOIN_CODE=4711", "ADMIN_USERNAMES=boss", "PUBLIC_ORIGIN=https://example.org",
        "server.port=0"})
@ActiveProfiles("cloud")
class ClassLimitTest {

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

    private int status(String url, HttpMethod method, String token, Object body) {
        return http.exchange(url, method, new HttpEntity<>(body, as(token)), Map.class).getStatusCode().value();
    }

    @Test
    @SuppressWarnings("unchecked")
    void aFullClassTakesNoOneMore() {
        String boss = (String) register("4711", "boss").get("token");
        assertThat(status("/api/learn/staff/teacher-code", HttpMethod.PUT, boss, Map.of("code", "MORIM2026"))).isEqualTo(200);
        Object danaId = idOf(register("MORIM2026", "dana"));
        assertThat(status("/api/learn/staff/teachers/" + danaId + "/approve", HttpMethod.POST, boss, null)).isEqualTo(200);
        String dana = login("dana");
        Map<String, Object> cls = http.exchange("/api/learn/staff/classes", HttpMethod.POST,
                new HttpEntity<>(Map.of("name", "ג׳1"), as(dana)), Map.class).getBody();
        Object classId = cls.get("id");
        String code = (String) cls.get("joinCode");
        String url = "/api/learn/staff/classes/" + classId;

        // only the admin sets the limit
        assertThat(status(url, HttpMethod.PATCH, dana, Map.of("maxStudents", 2))).isEqualTo(403);
        assertThat(status(url, HttpMethod.PATCH, boss, Map.of("maxStudents", 0))).isEqualTo(200); // 0 = no limit
        assertThat(status(url, HttpMethod.PATCH, boss, Map.of("maxStudents", 500))).isEqualTo(400);
        assertThat(status(url, HttpMethod.PATCH, boss, Map.of("maxStudents", 2))).isEqualTo(200);

        // three children sign up with the class code: all wait; the teacher approves 2, the 3rd is refused
        Object a = idOf(register(code, "kida")), b = idOf(register(code, "kidb")), c = idOf(register(code, "kidc"));
        assertThat(status("/api/learn/staff/children/" + a + "/approve", HttpMethod.POST, dana, null)).isEqualTo(200);
        assertThat(status("/api/learn/staff/children/" + b + "/approve", HttpMethod.POST, dana, null)).isEqualTo(200);
        assertThat(status("/api/learn/staff/children/" + c + "/approve", HttpMethod.POST, dana, null)).isEqualTo(409);
        assertThat(status("/api/learn/staff/children/" + c + "/approve", HttpMethod.POST, boss, null)).isEqualTo(409);
        assertThat(status("/api/learn/admin/children/" + c + "/approve", HttpMethod.POST, boss, null)).isEqualTo(409);
        // the limit can't go below who is already in
        assertThat(status(url, HttpMethod.PATCH, boss, Map.of("maxStudents", 1))).isEqualTo(400);

        // the class list shows the limit
        Map<String, Object> me = http.exchange("/api/learn/staff/me", HttpMethod.GET, new HttpEntity<>(as(dana)), Map.class).getBody();
        Map<String, Object> row = ((List<Map<String, Object>>) me.get("classes")).get(0);
        assertThat(row).containsEntry("maxStudents", 2).containsEntry("children", 2).containsEntry("waiting", 1);

        // the admin raises it to 3 -> the waiting child can be approved
        assertThat(status(url, HttpMethod.PATCH, boss, Map.of("maxStudents", 3))).isEqualTo(200);
        assertThat(status("/api/learn/staff/children/" + c + "/approve", HttpMethod.POST, dana, null)).isEqualTo(200);

        // moving: a out to another class (a seat frees), the limit back to 2, a can't come back in
        Object other = http.exchange("/api/learn/staff/classes", HttpMethod.POST,
                new HttpEntity<>(Map.of("name", "ג׳2"), as(dana)), Map.class).getBody().get("id");
        assertThat(status("/api/learn/staff/children/" + a + "/class", HttpMethod.POST, dana, Map.of("classId", other))).isEqualTo(200);
        assertThat(status(url, HttpMethod.PATCH, boss, Map.of("maxStudents", 2))).isEqualTo(200);
        assertThat(status("/api/learn/staff/children/" + a + "/class", HttpMethod.POST, dana, Map.of("classId", classId))).isEqualTo(409);
        assertThat(status("/api/learn/staff/children/" + a + "/class", HttpMethod.POST, boss, Map.of("classId", classId))).isEqualTo(409);
    }
}
