package com.example.brainblocks;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.brainblocks.learning.model.LearnClassWeek;
import com.example.brainblocks.learning.repository.LearnClassWeekRepository;
import java.time.LocalDate;
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
 * 2026-11-14 (Shir: "a class island in 3D, upgraded"): finished weeks stay on the class island as
 * buildings, past weeks are recorded once, and the league ranks classes by bricks per child.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "DATABASE_URL=jdbc:h2:mem:classisle;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "DATABASE_USER=sa", "DATABASE_PASSWORD=",
        "JOIN_CODE=4711", "ADMIN_USERNAMES=boss", "PUBLIC_ORIGIN=https://example.org",
        "server.port=0"})
@ActiveProfiles("cloud")
class ClassIslandTest {

    @Autowired TestRestTemplate http;
    @Autowired LearnClassWeekRepository weeks;
    @Autowired JdbcTemplate jdbc;

    private static HttpHeaders h(String token) {
        HttpHeaders h = new HttpHeaders();
        h.set("X-Learner-Token", token);
        return h;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> call(HttpMethod m, String path, Object body, String token, int expect) {
        var r = http.exchange(path, m, new HttpEntity<>(body, h(token)), Map.class);
        assertThat(r.getStatusCode().value()).as(m + " " + path).isEqualTo(expect);
        return r.getBody();
    }

    @SuppressWarnings("unchecked")
    private Long kidIn(Map<String, Object> cls, String user, String boss) {
        Map<String, Object> kid = http.postForObject("/api/learn/auth/register", new HashMap<>(Map.of("joinCode", cls.get("joinCode"),
                "username", user, "password", "secret12", "name", user, "avatar", "GIRL", "grade", "G3")), Map.class);
        Long id = ((Number) ((Map<String, Object>) kid.get("profile")).get("id")).longValue();
        call(HttpMethod.POST, "/api/learn/staff/children/" + id + "/approve", null, boss, 200);
        return id;
    }

    @Test
    void buildingsGrowAndLevelUp() {
        var b = com.example.brainblocks.learning.service.ClassIslandServiceAccess.buildings(3);
        assertThat(b).extracting(m -> m.get("kind")).containsExactly("school", "library", "fountain");
        assertThat(b).allMatch(m -> m.get("level").equals(1));
        // a full round (10) and two more: the first two grow to level 2
        var later = com.example.brainblocks.learning.service.ClassIslandServiceAccess.buildings(12);
        assertThat(later).hasSize(10);
        assertThat(later.get(0).get("level")).isEqualTo(2);
        assertThat(later.get(1).get("level")).isEqualTo(2);
        assertThat(later.get(2).get("level")).isEqualTo(1);
        assertThat(com.example.brainblocks.learning.service.ClassIslandServiceAccess.next(12))
                .containsEntry("kind", "fountain").containsEntry("level", 2).containsEntry("upgrade", true);
    }

    @Test
    @SuppressWarnings("unchecked")
    void theClassIslandAndTheLeague() {
        String boss = (String) http.postForObject("/api/learn/auth/register", new HashMap<>(Map.of("joinCode", "4711",
                "username", "boss", "password", "secret12", "name", "boss", "avatar", "GIRL")), Map.class).get("token");
        Map<String, Object> a = call(HttpMethod.POST, "/api/learn/staff/classes", Map.of("name", "ג׳1"), boss, 200);
        Map<String, Object> other = call(HttpMethod.POST, "/api/learn/staff/classes", Map.of("name", "ד׳2"), boss, 200);
        Long kid = kidIn(a, "kidisle", boss);
        kidIn(other, "kidother", boss);
        String k = (String) http.postForObject("/api/learn/auth/login", Map.of("username", "kidisle", "password", "secret12"), Map.class).get("token");
        Long classId = ((Number) a.get("id")).longValue();

        // the class was opened 3 weeks ago: 3 past weeks get recorded (nothing done), once
        jdbc.update("update learn_class set created_at = ? where id = ?", java.time.LocalDateTime.now().minusWeeks(3), classId);
        Map<String, Object> isle = call(HttpMethod.GET, "/api/learn/profiles/" + kid + "/class-island", null, k, 200);
        assertThat(isle).containsEntry("className", "ג׳1").containsEntry("weeksDone", 0).containsEntry("weeksTotal", 3);
        assertThat((List<Object>) isle.get("buildings")).isEmpty();
        assertThat((Map<String, Object>) isle.get("next")).containsEntry("kind", "school");
        call(HttpMethod.GET, "/api/learn/profiles/" + kid + "/class-island", null, k, 200);
        assertThat(weeks.findByClassIdOrderByWeekStartAsc(classId)).hasSize(3);

        // two finished weeks earlier in the year -> a school and a library on the island
        LocalDate old = LocalDate.now().minusWeeks(10);
        weeks.save(new LearnClassWeek(classId, old, 30, 30, true));
        weeks.save(new LearnClassWeek(classId, old.plusWeeks(1), 30, 30, true));
        isle = call(HttpMethod.GET, "/api/learn/profiles/" + kid + "/class-island", null, k, 200);
        assertThat(isle).containsEntry("weeksDone", 2);
        assertThat((List<Map<String, Object>>) isle.get("buildings")).extracting(m -> m.get("kind")).containsExactly("school", "library");
        assertThat((Map<String, Object>) isle.get("next")).containsEntry("kind", "fountain");

        // the league: both classes, this one marked; no child's name anywhere
        List<Map<String, Object>> league = (List<Map<String, Object>>) isle.get("league");
        assertThat(league).extracting(m -> m.get("name")).containsExactlyInAnyOrder("ג׳1", "ד׳2");
        assertThat(league).filteredOn(m -> Boolean.TRUE.equals(m.get("mine"))).extracting(m -> m.get("name")).containsExactly("ג׳1");
        assertThat(league.toString()).doesNotContain("kidisle", "kidother");

        // staff: the admin sees it; a child can't use the staff route; another child sees its own class
        assertThat(call(HttpMethod.GET, "/api/learn/staff/classes/" + classId + "/island", null, boss, 200)).containsEntry("weeksDone", 2);
        call(HttpMethod.GET, "/api/learn/staff/classes/" + classId + "/island", null, k, 403);
        call(HttpMethod.GET, "/api/learn/profiles/999999/class-island", null, k, 404);
    }
}
