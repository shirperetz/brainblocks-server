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
 * 2026-11-12 (Shir: "the teacher opens a room, the class comes in, also children who want to join -
 * she approves them; she sees live who leads; her own exercises"). End to end: a class child is in at
 * once, another class's child and guests wait for her; the right answer never reaches the phone; points,
 * the live table, the board; another teacher / a child / a fake guest token are refused.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "DATABASE_URL=jdbc:h2:mem:liveroom;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "DATABASE_USER=sa", "DATABASE_PASSWORD=",
        "JOIN_CODE=4711", "ADMIN_USERNAMES=boss", "PUBLIC_ORIGIN=https://example.org",
        "server.port=0"})
@ActiveProfiles("cloud")
class LiveRoomTest {

    @Autowired
    TestRestTemplate http;

    private HttpHeaders as(String token) {
        HttpHeaders h = new HttpHeaders();
        if (token != null) h.set("X-Learner-Token", token);
        return h;
    }

    private HttpHeaders guest(String token) {
        HttpHeaders h = new HttpHeaders();
        h.set("X-Guest-Token", token);
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

    @Test
    @SuppressWarnings("unchecked")
    void aWholeLessonInALiveRoom() {
        String boss = (String) register("4711", "boss", "G6").get("token");
        assertThat(status("/api/learn/staff/teacher-code", HttpMethod.PUT, as(boss), Map.of("code", "MORIM2026"))).isEqualTo(200);
        for (String t : List.of("dana", "rina")) {
            Object id = ((Map<String, Object>) register("MORIM2026", t, null).get("profile")).get("id");
            assertThat(status("/api/learn/staff/teachers/" + id + "/approve", HttpMethod.POST, as(boss), null)).isEqualTo(200);
        }
        String dana = login("dana");
        String rina = login("rina");
        Map<String, Object> cls = call("/api/learn/staff/classes", HttpMethod.POST, as(dana), Map.of("name", "ד׳1", "grade", "G4"));
        Object kidId = ((Map<String, Object>) register((String) cls.get("joinCode"), "kid", "G4").get("profile")).get("id");
        assertThat(status("/api/learn/staff/children/" + kidId + "/approve", HttpMethod.POST, as(dana), null)).isEqualTo(200);
        String k = login("kid");
        Map<String, Object> rinaCls = call("/api/learn/staff/classes", HttpMethod.POST, as(rina), Map.of("name", "ו׳2"));
        Object otherId = ((Map<String, Object>) register((String) rinaCls.get("joinCode"), "other", "G6").get("profile")).get("id");
        assertThat(status("/api/learn/staff/children/" + otherId + "/approve", HttpMethod.POST, as(rina), null)).isEqualTo(200);
        String o = login("other");

        // Dana's own exercises: a numeric one (wrong answers made up) and a word one.
        assertThat(status("/api/learn/rooms/my-questions", HttpMethod.POST, as(dana), Map.of("text", "כמה רגליים לשלוש חתולות?", "correct", "12"))).isEqualTo(200);
        assertThat(status("/api/learn/rooms/my-questions", HttpMethod.POST, as(dana), Map.of("text", "איזו צורה עגולה?", "correct", "עיגול"))).isEqualTo(400);
        assertThat(status("/api/learn/rooms/my-questions", HttpMethod.POST, as(dana), Map.of("text", "איזו צורה עגולה?", "correct", "עיגול", "wrong1", "ריבוע", "wrong2", "משולש"))).isEqualTo(200);
        assertThat(status("/api/learn/rooms/my-questions", HttpMethod.POST, as(k), Map.of("text", "x", "correct", "1"))).isEqualTo(403);
        List<Map<String, Object>> mine = http.exchange("/api/learn/rooms/my-questions", HttpMethod.GET, new HttpEntity<>(as(dana)), List.class).getBody();
        assertThat(mine).hasSize(2);

        // Bad rooms are refused; a topic of another grade too (ד׳ has no "חישוב זוויות").
        assertThat(status("/api/learn/rooms", HttpMethod.POST, as(dana), Map.of("classId", cls.get("id"), "topics", List.of()))).isEqualTo(400);
        assertThat(status("/api/learn/rooms", HttpMethod.POST, as(dana), Map.of("classId", cls.get("id"), "topics", List.of("ANGLE_CALC")))).isEqualTo(400);
        assertThat(status("/api/learn/rooms", HttpMethod.POST, as(dana), Map.of("classId", rinaCls.get("id"), "topics", List.of("ADDITION")))).isEqualTo(403);
        assertThat(status("/api/learn/rooms", HttpMethod.POST, as(k), Map.of("topics", List.of("ADDITION")))).isEqualTo(403);

        // Dana opens a room with only her own exercises (so the test knows the answers).
        Map<String, Object> room = call("/api/learn/rooms", HttpMethod.POST, as(dana), Map.of("classId", cls.get("id"), "topics", List.of("MY"), "minutes", 5));
        String code = (String) room.get("code");
        String live = "/api/learn/rooms/" + room.get("id") + "/live";
        assertThat(code).matches("\\d{6}");

        // Her class's child: straight in. Another class's child and guests: waiting.
        assertThat(call("/api/learn/rooms/join", HttpMethod.POST, as(k), Map.of("code", code))).containsEntry("status", "IN");
        assertThat(call("/api/learn/rooms/join", HttpMethod.POST, as(o), Map.of("code", code))).containsEntry("status", "WAITING");
        Map<String, Object> g1 = call("/api/learn/room-guest/join", HttpMethod.POST, new HttpHeaders(), Map.of("code", code, "name", "רון"));
        Map<String, Object> g2 = call("/api/learn/room-guest/join", HttpMethod.POST, new HttpHeaders(), Map.of("code", code, "name", "זר"));
        assertThat(g1).containsEntry("status", "WAITING").containsKey("guestToken");
        String gt = (String) g1.get("guestToken");
        assertThat(status("/api/learn/room-guest/join", HttpMethod.POST, new HttpHeaders(), Map.of("code", "000000", "name", "x"))).isEqualTo(404);

        Map<String, Object> lobby = call(live, HttpMethod.GET, as(dana), null);
        assertThat(lobby).containsEntry("state", "LOBBY");
        assertThat((List<?>) lobby.get("players")).hasSize(1);
        List<Map<String, Object>> waiting = (List<Map<String, Object>>) lobby.get("waiting");
        assertThat(waiting).extracting(w -> w.get("name")).containsExactly("other", "רון", "זר");
        // Only Dana (and the admin) see her room.
        assertThat(status(live, HttpMethod.GET, as(rina), null)).isEqualTo(403);
        assertThat(status(live, HttpMethod.GET, as(k), null)).isEqualTo(403);
        assertThat(status(live, HttpMethod.GET, as(boss), null)).isEqualTo(200);

        // She approves "other" and Ron, refuses the stranger.
        Object pOther = waiting.get(0).get("id"), pRon = waiting.get(1).get("id"), pStranger = waiting.get(2).get("id");
        String base = "/api/learn/rooms/" + room.get("id") + "/players/";
        assertThat(status(base + pOther + "/approve", HttpMethod.POST, as(dana), null)).isEqualTo(200);
        assertThat(status(base + pRon + "/approve", HttpMethod.POST, as(dana), null)).isEqualTo(200);
        assertThat(status(base + pStranger + "/reject", HttpMethod.POST, as(dana), null)).isEqualTo(200);
        assertThat(call("/api/learn/room-guest/" + code + "/me", HttpMethod.GET, guest((String) g2.get("guestToken")), null)).containsEntry("status", "REJECTED");

        // Not started: no questions yet.
        assertThat(status("/api/learn/rooms/code/" + code + "/question", HttpMethod.GET, as(k), null)).isEqualTo(409);
        assertThat(call("/api/learn/rooms/" + room.get("id") + "/start", HttpMethod.POST, as(dana), null)).containsEntry("state", "RUNNING");

        // The phone never gets the right answer; the server checks it.
        Map<String, Object> q = call("/api/learn/rooms/code/" + code + "/question", HttpMethod.GET, as(k), null);
        assertThat(q).doesNotContainKey("correctIndex").containsKeys("text", "options");
        int kidPoints = 0;
        for (int i = 0; i < 6; i++) {
            q = call("/api/learn/rooms/code/" + code + "/question", HttpMethod.GET, as(k), null);
            String right = q.get("text").toString().contains("רגליים") ? "12" : "עיגול";
            Map<String, Object> a = call("/api/learn/rooms/code/" + code + "/answer", HttpMethod.POST, as(k), Map.of("answer", right));
            assertThat(a).containsEntry("correct", true);
            kidPoints = (Integer) a.get("points");
        }
        assertThat(kidPoints).isEqualTo(6 * 10 + 4 * 5); // 10 each, +5 from the third in a row

        // Ron (guest) gets one wrong - the same exercise comes back later, the retry earns nothing.
        q = call("/api/learn/room-guest/" + code + "/question", HttpMethod.GET, guest(gt), null);
        String wrongFirst = q.get("text").toString();
        String bad = ((List<String>) q.get("options")).stream().filter(x -> !x.equals("12") && !x.equals("עיגול")).findFirst().orElseThrow();
        assertThat(call("/api/learn/room-guest/" + code + "/answer", HttpMethod.POST, guest(gt), Map.of("answer", bad)))
                .containsEntry("correct", false);
        boolean sawRetry = false;
        for (int i = 0; i < 4; i++) {
            q = call("/api/learn/room-guest/" + code + "/question", HttpMethod.GET, guest(gt), null);
            String right = q.get("text").toString().contains("רגליים") ? "12" : "עיגול";
            Map<String, Object> a = call("/api/learn/room-guest/" + code + "/answer", HttpMethod.POST, guest(gt), Map.of("answer", right));
            if (Boolean.TRUE.equals(q.get("retry"))) {
                sawRetry = true;
                assertThat(q.get("text")).isEqualTo(wrongFirst);
                assertThat(a).containsEntry("gained", 0);
            }
        }
        assertThat(sawRetry).isTrue();
        assertThat(status("/api/learn/room-guest/" + code + "/question", HttpMethod.GET, guest("fake"), null)).isEqualTo(401);
        assertThat(status("/api/learn/room-guest/" + code + "/question", HttpMethod.GET, guest((String) g2.get("guestToken")), null)).isEqualTo(409);

        // The live table: the kid leads; the board shows the top five only.
        Map<String, Object> now = call(live, HttpMethod.GET, as(dana), null);
        List<Map<String, Object>> rows = (List<Map<String, Object>>) now.get("players");
        assertThat(rows.get(0)).containsEntry("name", "kid").containsEntry("place", 1).containsEntry("points", kidPoints);
        assertThat(rows).hasSize(3);
        assertThat((Integer) now.get("answered")).isGreaterThanOrEqualTo(9);
        Map<String, Object> board = call("/api/learn/rooms/" + room.get("id") + "/board", HttpMethod.GET, as(dana), null);
        assertThat((List<?>) board.get("top")).hasSizeLessThanOrEqualTo(5);

        // The kid's answers are saved (the class building counts them); the guest's are not.
        Map<String, Object> building = call("/api/learn/profiles/" + kidId + "/class-building", HttpMethod.GET, as(k), null);
        assertThat(((Number) building.get("mine")).intValue()).isEqualTo(6);

        // End: no more answers.
        assertThat(call("/api/learn/rooms/" + room.get("id") + "/end", HttpMethod.POST, as(dana), null)).containsEntry("state", "ENDED");
        assertThat(status("/api/learn/rooms/code/" + code + "/question", HttpMethod.GET, as(k), null)).isEqualTo(409);
        assertThat(call("/api/learn/rooms/code/" + code + "/me", HttpMethod.GET, as(k), null)).containsEntry("state", "ENDED").containsEntry("points", kidPoints);
    }
}
