package com.example.brainblocks;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.brainblocks.learning.model.Avatar;
import com.example.brainblocks.learning.repository.LearnerProfileRepository;
import com.example.brainblocks.learning.service.AccountService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

/** 2026-11-26 (idea #9): a teacher's read-only link for parents - works without log-in, only for that child, can be cancelled. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "DATABASE_URL=jdbc:h2:mem:award;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "DATABASE_USER=sa", "DATABASE_PASSWORD=", "JOIN_CODE=4711", "ADMIN_USERNAMES=boss",
        "PUBLIC_ORIGIN=https://example.org", "ADMIN_2FA=off", "server.port=0"})
@ActiveProfiles("cloud")
class AwardTest {

    @Autowired TestRestTemplate http;
    @Autowired AccountService accounts;
    @Autowired LearnerProfileRepository profiles;

    private ResponseEntity<Map> call(HttpMethod m, String path, String token) {
        HttpHeaders h = new HttpHeaders();
        h.add("Origin", "https://example.org");
        if (token != null) h.add("X-Learner-Token", token);
        return http.exchange(path, m, new HttpEntity<>(h), Map.class);
    }

    @Test
    private ResponseEntity<Map> post(String path, Object body, String token) {
        HttpHeaders h = new HttpHeaders();
        h.add("Origin", "https://example.org");
        h.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) h.add("X-Learner-Token", token);
        return http.exchange(path, HttpMethod.POST, new HttpEntity<>(body, h), Map.class);
    }

    private int coins(Long id, String token) {
        return ((Number) call(HttpMethod.GET, "/api/learn/profiles/" + id + "/my-island", token).getBody().get("coins")).intValue();
    }

    @Test
    void aTeacherPrizeReachesTheChildOnceWithCoinsAndTheClassToo() {
        String boss = accounts.register("4711", "boss", "pass1234567", "שיר", Avatar.values()[0].name(), "G5", "MEDIUM").token();
        Long kid = accounts.register("4711", "awkid", "pass12345", "דניאל", Avatar.values()[0].name(), "G3", "MEDIUM").profile().getId();
        Long kid2 = accounts.register("4711", "awkid2", "pass12345", "מאיה", Avatar.values()[0].name(), "G3", "MEDIUM").profile().getId();
        for (Long id : List.of(kid, kid2)) { var p = profiles.findById(id).orElseThrow(); p.setApproved(true); profiles.save(p); }
        String kt = accounts.login("awkid", "pass12345", java.time.Instant.now()).token();
        String kt2 = accounts.login("awkid2", "pass12345", java.time.Instant.now()).token();
        int before = coins(kid, kt);

        // a child can't give prizes
        assertThat(post("/api/learn/staff/awards", Map.of("childId", kid2, "coins", 20, "message", "x"), kt).getStatusCode().value()).isEqualTo(403);
        // wrong coins / emoji refused
        assertThat(post("/api/learn/staff/awards", Map.of("childId", kid, "coins", 999), boss).getStatusCode().value()).isEqualTo(400);
        assertThat(post("/api/learn/staff/awards", Map.of("childId", kid, "emoji", "💩", "coins", 5), boss).getStatusCode().value()).isEqualTo(400);

        var ok = post("/api/learn/staff/awards", Map.of("childId", kid, "emoji", "🏆", "coins", 10, "message", "כל הכבוד על ההתמדה‮!"), boss);
        assertThat(ok.getStatusCode().value()).isEqualTo(200);
        assertThat(coins(kid, kt)).isEqualTo(before + 10);
        List<Map> unseen = http.exchange("/api/learn/profiles/" + kid + "/awards", HttpMethod.GET, entity(kt), List.class).getBody();
        assertThat(unseen).hasSize(1);
        assertThat(unseen.get(0).get("message")).isEqualTo("כל הכבוד על ההתמדה !");
        assertThat(unseen.get(0).get("from")).isEqualTo("שיר");
        // another child can't read it
        assertThat(http.exchange("/api/learn/profiles/" + kid + "/awards", HttpMethod.GET, entity(kt2), String.class).getStatusCode().value()).isEqualTo(404);
        post("/api/learn/profiles/" + kid + "/awards/seen", Map.of(), kt);
        assertThat(http.exchange("/api/learn/profiles/" + kid + "/awards", HttpMethod.GET, entity(kt), List.class).getBody()).isEmpty();

        // the whole class
        Long classId = ((Number) post("/api/learn/staff/classes", Map.of("name", "ג1"), boss).getBody().get("id")).intValue() * 1L;
        for (Long id : List.of(kid, kid2)) post("/api/learn/staff/children/" + id + "/class", Map.of("classId", classId), boss);
        var cls = post("/api/learn/staff/awards", Map.of("classId", classId, "emoji", "🎉", "coins", 5, "message", "הכיתה הכי טובה"), boss);
        assertThat(((Number) cls.getBody().get("given")).intValue()).isEqualTo(2);
        assertThat(coins(kid2, kt2)).isGreaterThanOrEqualTo(5);

        // at most 3 a day for one child
        post("/api/learn/staff/awards", Map.of("childId", kid, "coins", 5), boss);
        var fourth = post("/api/learn/staff/awards", Map.of("childId", kid, "coins", 5), boss);
        assertThat(((Number) fourth.getBody().get("given")).intValue()).isZero();
        assertThat(fourth.getBody().get("skipped").toString()).isEqualTo("[דניאל]");
    }

    private HttpEntity<Void> entity(String token) {
        HttpHeaders h = new HttpHeaders();
        h.add("Origin", "https://example.org");
        h.add("X-Learner-Token", token);
        return new HttpEntity<>(h);
    }
}
