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
 * 2026-11-12 (Shir: "in the admin screen I make my users teachers, and under each teacher the children
 * of her class"): the tree teacher -> class -> children, "no teacher", promote/demote, move a class.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "DATABASE_URL=jdbc:h2:mem:admintree;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "DATABASE_USER=sa", "DATABASE_PASSWORD=",
        "JOIN_CODE=4711", "ADMIN_USERNAMES=boss", "PUBLIC_ORIGIN=https://example.org",
        "server.port=0"})
@ActiveProfiles("cloud")
class AdminTreeTest {

    @Autowired
    TestRestTemplate http;

    private HttpHeaders as(String token) {
        HttpHeaders h = new HttpHeaders();
        if (token != null) h.set("X-Learner-Token", token);
        return h;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> call(String url, HttpMethod m, String token, Object body) {
        return http.exchange(url, m, new HttpEntity<>(body, as(token)), Map.class).getBody();
    }

    private int status(String url, HttpMethod m, String token, Object body) {
        return http.exchange(url, m, new HttpEntity<>(body, as(token)), String.class).getStatusCode().value();
    }

    @SuppressWarnings("unchecked")
    private Object register(String code, String user) {
        Map<String, Object> b = new HashMap<>(Map.of("joinCode", code, "username", user, "password", "secret12", "name", user, "avatar", "GIRL", "grade", "G3"));
        return ((Map<String, Object>) call("/api/learn/auth/register", HttpMethod.POST, null, b).get("profile")).get("id");
    }

    private String login(String user) {
        return (String) call("/api/learn/auth/login", HttpMethod.POST, null, Map.of("username", user, "password", "secret12")).get("token");
    }

    @Test
    @SuppressWarnings("unchecked")
    void teachersClassesChildren() {
        register("4711", "boss");
        String boss = login("boss");
        Object anna = register("4711", "anna");     // will become a teacher
        Object solo = register("4711", "solo");     // stays without a teacher
        call("/api/learn/staff/children/" + anna + "/approve", HttpMethod.POST, boss, null);
        call("/api/learn/staff/children/" + solo + "/approve", HttpMethod.POST, boss, null);

        // Only the admin sees the tree / promotes.
        String soloT = login("solo");
        assertThat(status("/api/learn/admin/tree", HttpMethod.GET, soloT, null)).isEqualTo(403);
        assertThat(status("/api/learn/admin/children/" + anna + "/teacher", HttpMethod.POST, soloT, Map.of("teacher", true))).isEqualTo(403);

        // anna becomes a teacher, opens a class, a child joins it.
        assertThat(call("/api/learn/admin/children/" + anna + "/teacher", HttpMethod.POST, boss, Map.of("teacher", true))).containsEntry("teacher", true);
        String annaT = login("anna");
        Map<String, Object> cls = call("/api/learn/staff/classes", HttpMethod.POST, annaT, Map.of("name", "ג׳1"));
        Object kid = register((String) cls.get("joinCode"), "kid");
        call("/api/learn/staff/children/" + kid + "/approve", HttpMethod.POST, annaT, null);

        Map<String, Object> tree = call("/api/learn/admin/tree", HttpMethod.GET, boss, null);
        List<Map<String, Object>> teachers = (List<Map<String, Object>>) tree.get("teachers");
        Map<String, Object> annaNode = teachers.stream().filter(t -> anna.equals(t.get("id"))).findFirst().orElseThrow();
        List<Map<String, Object>> annaClasses = (List<Map<String, Object>>) annaNode.get("classes");
        assertThat(annaClasses).hasSize(1);
        assertThat((List<Map<String, Object>>) annaClasses.get(0).get("children")).extracting(c -> c.get("id")).containsExactly(kid);
        List<Map<String, Object>> noTeacher = (List<Map<String, Object>>) tree.get("noTeacher");
        assertThat(noTeacher).extracting(c -> c.get("id")).contains(solo).doesNotContain(kid, anna);

        // anna can't go back to student while she has a class; after it moves to the admin, she can.
        assertThat(status("/api/learn/admin/children/" + anna + "/teacher", HttpMethod.POST, boss, Map.of("teacher", false))).isEqualTo(409);
        Object bossId = teachers.stream().filter(t -> Boolean.TRUE.equals(t.get("admin"))).findFirst().orElseThrow().get("id");
        assertThat(status("/api/learn/admin/classes/" + cls.get("id") + "/teacher", HttpMethod.POST, boss, Map.of("teacherId", bossId))).isEqualTo(200);
        assertThat(call("/api/learn/admin/children/" + anna + "/teacher", HttpMethod.POST, boss, Map.of("teacher", false))).containsEntry("teacher", false);

        // The admin can't be turned into anything else here.
        assertThat(status("/api/learn/admin/children/" + bossId + "/teacher", HttpMethod.POST, boss, Map.of("teacher", true))).isEqualTo(400);
    }
}
