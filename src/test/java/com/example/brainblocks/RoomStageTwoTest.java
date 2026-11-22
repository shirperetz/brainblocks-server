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
 * 2026-11-22 (Shir: "do them" - the room's stage two + the countdown): 3-2-1 before the first question,
 * two balanced groups with team points on the board, "stuck" with its reason, coins for the island, a
 * saved room opened again, "השתפרתי" in the next room, the teacher's summary, and the weekly report.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "DATABASE_URL=jdbc:h2:mem:roomtwo;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "DATABASE_USER=sa", "DATABASE_PASSWORD=",
        "JOIN_CODE=4711", "ADMIN_USERNAMES=boss", "PUBLIC_ORIGIN=https://example.org",
        "server.port=0", "brainblocks.room-countdown-ms=1500"})
@ActiveProfiles("cloud")
class RoomStageTwoTest {

    @Autowired
    TestRestTemplate http;

    private HttpHeaders as(String token) {
        HttpHeaders h = new HttpHeaders();
        if (token != null) h.set("X-Learner-Token", token);
        return h;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> register(String code, String user, String grade) {
        Map<String, Object> body = new HashMap<>(Map.of("joinCode", code, "username", user, "password", "secret12", "name", user, "avatar", "GIRL"));
        if (grade != null) body.put("grade", grade);
        return http.postForObject("/api/learn/auth/register", body, Map.class);
    }

    @SuppressWarnings("unchecked")
    private String login(String user) {
        return (String) http.postForObject("/api/learn/auth/login", Map.of("username", user, "password", "secret12"), Map.class).get("token");
    }

    private int status(String url, HttpMethod m, HttpHeaders h, Object body) {
        return http.exchange(url, m, new HttpEntity<>(body, h), String.class).getStatusCode().value();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> call(String url, HttpMethod m, HttpHeaders h, Object body) {
        return http.exchange(url, m, new HttpEntity<>(body, h), Map.class).getBody();
    }

    @SuppressWarnings("unchecked")
    private int coins(String token) {
        Map<String, Object> s = call("/api/learn/profiles/" + profileOf(token) + "/my-island?isle=0", HttpMethod.GET, as(token), null);
        return ((Number) s.get("coins")).intValue();
    }

    private final Map<String, Object> ids = new HashMap<>();

    private Object profileOf(String token) {
        return ids.get(token);
    }

    /** Answers one question: right = the teacher's numeric exercise's answer, otherwise a wrong one. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> answer(String code, String token, boolean right) {
        Map<String, Object> q = call("/api/learn/rooms/code/" + code + "/question", HttpMethod.GET, as(token), null);
        List<String> options = (List<String>) q.get("options");
        String pick = right ? "12" : options.stream().filter(o -> !o.equals("12")).findFirst().orElseThrow();
        return call("/api/learn/rooms/code/" + code + "/answer", HttpMethod.POST, as(token), Map.of("answer", pick));
    }

    @Test
    @SuppressWarnings("unchecked")
    void stageTwoInOneLesson() throws Exception {
        String boss = (String) register("4711", "boss", "G6").get("token");
        assertThat(status("/api/learn/staff/teacher-code", HttpMethod.PUT, as(boss), Map.of("code", "MORIM2026"))).isEqualTo(200);
        Object tid = ((Map<String, Object>) register("MORIM2026", "dana", null).get("profile")).get("id");
        assertThat(status("/api/learn/staff/teachers/" + tid + "/approve", HttpMethod.POST, as(boss), null)).isEqualTo(200);
        String dana = login("dana");
        Map<String, Object> cls = call("/api/learn/staff/classes", HttpMethod.POST, as(dana), Map.of("name", "ד׳1", "grade", "G4"));
        String[] kids = {"noa", "itai", "maya", "yoni"};
        String[] tok = new String[4];
        for (int i = 0; i < 4; i++) {
            Object id = ((Map<String, Object>) register((String) cls.get("joinCode"), kids[i], "G4").get("profile")).get("id");
            assertThat(status("/api/learn/staff/children/" + id + "/approve", HttpMethod.POST, as(dana), null)).isEqualTo(200);
            tok[i] = login(kids[i]);
            ids.put(tok[i], id);
        }
        assertThat(status("/api/learn/rooms/my-questions", HttpMethod.POST, as(dana), Map.of("text", "כמה רגליים לשלוש חתולות?", "correct", "12"))).isEqualTo(200);

        // #10: a saved room - only its teacher sees it; a child can't save one
        Map<String, Object> tpl = Map.of("name", "רגליים", "classId", cls.get("id"), "topics", List.of("MY"), "minutes", 5, "coins", true, "teams", true);
        assertThat(status("/api/learn/rooms/templates", HttpMethod.POST, as(tok[0]), tpl)).isEqualTo(403);
        Object templateId = call("/api/learn/rooms/templates", HttpMethod.POST, as(dana), tpl).get("id");
        List<Map<String, Object>> saved = http.exchange("/api/learn/rooms/templates", HttpMethod.GET, new HttpEntity<>(as(dana)), List.class).getBody();
        assertThat(saved).hasSize(1).first().satisfies(t -> assertThat(t).containsEntry("name", "רגליים").containsEntry("teams", true));
        assertThat(status("/api/learn/rooms/warmup", HttpMethod.POST, as(dana), Map.of("grade", "G4"))).isEqualTo(200);
        assertThat(status("/api/learn/rooms/warmup", HttpMethod.POST, as(tok[0]), Map.of())).isEqualTo(403);

        // Opened from the saved room: coins + groups on
        Map<String, Object> room = call("/api/learn/rooms/templates/" + templateId + "/open", HttpMethod.POST, as(dana), null);
        String code = (String) room.get("code");
        String live = "/api/learn/rooms/" + room.get("id") + "/live";
        for (String t : tok) assertThat(call("/api/learn/rooms/join", HttpMethod.POST, as(t), Map.of("code", code))).containsEntry("status", "IN");
        int coinsBefore = coins(tok[0]);

        // 3-2-1: right after "start" no question yet, the countdown is on every screen
        call("/api/learn/rooms/" + room.get("id") + "/start", HttpMethod.POST, as(dana), null);
        Map<String, Object> me = call("/api/learn/rooms/code/" + code + "/me", HttpMethod.GET, as(tok[0]), null);
        assertThat(((Number) me.get("countdownMs")).longValue()).isPositive();
        assertThat(status("/api/learn/rooms/code/" + code + "/question", HttpMethod.GET, as(tok[0]), null)).isEqualTo(409);
        Thread.sleep(1700);

        // #9: two groups of two, and each child knows its group
        Map<String, Object> l = call(live, HttpMethod.GET, as(dana), null);
        List<Map<String, Object>> teams = (List<Map<String, Object>>) l.get("teamScores");
        assertThat(teams).hasSize(2).allSatisfy(t -> assertThat(t.get("players")).isEqualTo(2));
        assertThat(call("/api/learn/rooms/code/" + code + "/me", HttpMethod.GET, as(tok[1]), null).get("team")).isNotNull();

        // noa: 4 right; itai: 3 wrong in a row -> stuck with the reason; maya 1 right, yoni nothing
        for (int i = 0; i < 4; i++) assertThat(answer(code, tok[0], true)).containsEntry("correct", true);
        for (int i = 0; i < 3; i++) answer(code, tok[1], false);
        answer(code, tok[2], true);
        l = call(live, HttpMethod.GET, as(dana), null);
        Map<String, Object> itai = ((List<Map<String, Object>>) l.get("players")).stream().filter(p -> "itai".equals(p.get("name"))).findFirst().orElseThrow();
        assertThat(itai).containsEntry("stuck", true).containsEntry("stuckWhy", "3 טעויות ברצף");
        Map<String, Object> board = call("/api/learn/rooms/" + room.get("id") + "/board", HttpMethod.GET, as(dana), null);
        assertThat(((List<?>) board.get("teamScores"))).hasSize(2);

        // End: the summary, and coins for who played (5, +10 for places 1-3); yoni didn't answer - nothing
        call("/api/learn/rooms/" + room.get("id") + "/end", HttpMethod.POST, as(dana), null);
        l = call(live, HttpMethod.GET, as(dana), null);
        Map<String, Object> summary = (Map<String, Object>) l.get("summary");
        assertThat(summary.get("hardTopic")).isEqualTo("MIXED".equals(summary.get("hardTopic")) ? null : summary.get("hardTopic"));
        assertThat((List<Map<String, Object>>) summary.get("needHelp")).extracting(m -> m.get("name")).containsExactly("itai");
        assertThat(l.get("classId")).isEqualTo(cls.get("id"));
        assertThat(coins(tok[0])).isEqualTo(coinsBefore + 15);
        Map<String, Object> noaEnd = call("/api/learn/rooms/code/" + code + "/me", HttpMethod.GET, as(tok[0]), null);
        assertThat(noaEnd).containsEntry("coins", 15).containsEntry("improved", false);
        call(live, HttpMethod.GET, as(dana), null); // a second look gives no coins twice
        assertThat(coins(tok[0])).isEqualTo(coinsBefore + 15);

        // #3: the next room - itai gets 2 right (0 last time) -> "השתפרתי"
        Map<String, Object> room2 = call("/api/learn/rooms", HttpMethod.POST, as(dana), Map.of("classId", cls.get("id"), "topics", List.of("MY"), "minutes", 5));
        String code2 = (String) room2.get("code");
        call("/api/learn/rooms/join", HttpMethod.POST, as(tok[1]), Map.of("code", code2));
        call("/api/learn/rooms/" + room2.get("id") + "/start", HttpMethod.POST, as(dana), null);
        Thread.sleep(1700);
        answer(code2, tok[1], true);
        answer(code2, tok[1], true);
        call("/api/learn/rooms/" + room2.get("id") + "/end", HttpMethod.POST, as(dana), null);
        Map<String, Object> itaiEnd = call("/api/learn/rooms/code/" + code2 + "/me", HttpMethod.GET, as(tok[1]), null);
        assertThat(itaiEnd).containsEntry("improved", true).containsEntry("prevRight", 0).containsEntry("coins", 0);
        Map<String, Object> s2 = (Map<String, Object>) call("/api/learn/rooms/" + room2.get("id") + "/live", HttpMethod.GET, as(dana), null).get("summary");
        assertThat((List<Map<String, Object>>) s2.get("improved")).extracting(m -> m.get("name")).containsExactly("itai");

        // The weekly report: who practised, who didn't
        Map<String, Object> week = call("/api/learn/staff/classes/" + cls.get("id") + "/weekly", HttpMethod.GET, as(dana), null);
        assertThat((List<Map<String, Object>>) week.get("notPracticed")).extracting(m -> m.get("name")).containsExactly("yoni");
        assertThat(((List<?>) week.get("practiced"))).hasSize(3);
        assertThat(status("/api/learn/staff/classes/" + cls.get("id") + "/weekly", HttpMethod.GET, as(tok[0]), null)).isEqualTo(403);

        // the saved room can be deleted by its teacher only
        assertThat(status("/api/learn/rooms/templates/" + templateId + "/delete", HttpMethod.POST, as(boss), null)).isEqualTo(404);
        assertThat(status("/api/learn/rooms/templates/" + templateId + "/delete", HttpMethod.POST, as(dana), null)).isEqualTo(200);
    }
}
