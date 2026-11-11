package com.example.brainblocks;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/**
 * 2026-11-11 (Shir: "teachers look at their students, I look at mine, and I oversee everything";
 * "what she finds hard and what she doesn't"): end to end on an empty in-memory database -
 * teachers sign up with the teachers' code and wait for the admin, open classes, children sign up
 * with a class code and wait for THAT teacher; a teacher never sees or touches another class; the
 * report says which skill is hard, from real submitted answers.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "DATABASE_URL=jdbc:h2:mem:teachers;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "DATABASE_USER=sa", "DATABASE_PASSWORD=",
        "JOIN_CODE=4711", "ADMIN_USERNAMES=boss", "PUBLIC_ORIGIN=https://example.org",
        "server.port=0"})
@ActiveProfiles("cloud")
class TeachersAndReportsTest {

    @Autowired
    TestRestTemplate http;

    private HttpHeaders as(String token) {
        HttpHeaders h = new HttpHeaders();
        h.set("X-Learner-Token", token);
        return h;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> register(String code, String user, String name, String grade) {
        Map<String, Object> body = new java.util.HashMap<>(Map.of("joinCode", code, "username", user,
                "password", "secret12", "name", name, "avatar", "GIRL"));
        if (grade != null) body.put("grade", grade);
        return http.postForObject("/api/learn/auth/register", body, Map.class);
    }

    @SuppressWarnings("unchecked")
    private String login(String user) {
        Map<String, Object> r = http.postForObject("/api/learn/auth/login", Map.of("username", user, "password", "secret12"), Map.class);
        return (String) r.get("token");
    }

    private int status(String url, HttpMethod method, String token, Object body) {
        return http.exchange(url, method, new HttpEntity<>(body, as(token)), Map.class).getStatusCode().value();
    }

    @SuppressWarnings("unchecked")
    private <T> T get(String url, String token, Class<T> type) {
        return http.exchange(url, HttpMethod.GET, new HttpEntity<>(as(token)), type).getBody();
    }

    @Test
    @SuppressWarnings("unchecked")
    void teachersSeeOnlyTheirOwnClassAndTheReportFindsWhatIsHard() {
        String boss = (String) register("4711", "boss", "שיר", "G6").get("token");
        assertThat(status("/api/learn/staff/teacher-code", HttpMethod.PUT, boss, Map.of("code", "MORIM2026"))).isEqualTo(200);

        // Two teachers: wait for the admin, then log in as teachers.
        for (String t : List.of("dana", "rina")) {
            Map<String, Object> r = register("MORIM2026", t, t, null);
            assertThat(r.get("token")).as("waits for approval").isNull();
            assertThat(((Map<String, Object>) r.get("profile")).get("teacher")).isEqualTo(true);
            Object id = ((Map<String, Object>) r.get("profile")).get("id");
            assertThat(status("/api/learn/staff/teachers/" + id + "/approve", HttpMethod.POST, boss, null)).isEqualTo(200);
        }
        String dana = login("dana");
        String rina = login("rina");

        Map<String, Object> danaClass = http.exchange("/api/learn/staff/classes", HttpMethod.POST,
                new HttpEntity<>(Map.of("name", "ה׳2"), as(dana)), Map.class).getBody();
        Map<String, Object> rinaClass = http.exchange("/api/learn/staff/classes", HttpMethod.POST,
                new HttpEntity<>(Map.of("name", "ו׳1"), as(rina)), Map.class).getBody();
        String danaCode = (String) danaClass.get("joinCode");
        assertThat(danaCode).hasSize(6);

        // A child of Dana's class waits for Dana; Rina can neither see nor approve her.
        Map<String, Object> noa = register(danaCode, "noa", "נועה", "G3");
        Object noaId = ((Map<String, Object>) noa.get("profile")).get("id");
        assertThat(noa.get("token")).isNull();
        assertThat(status("/api/learn/staff/children/" + noaId + "/approve", HttpMethod.POST, rina, null)).isEqualTo(403);
        assertThat(get("/api/learn/staff/children", rina, List.class)).isEmpty();
        assertThat(get("/api/learn/staff/children", dana, List.class)).hasSize(1);
        assertThat(status("/api/learn/staff/children/" + noaId + "/approve", HttpMethod.POST, dana, null)).isEqualTo(200);
        String noaToken = login("noa");

        // A child is not staff.
        assertThat(status("/api/learn/staff/me", HttpMethod.GET, noaToken, null)).isEqualTo(403);
        assertThat(status("/api/learn/staff/children/" + noaId + "/report", HttpMethod.GET, noaToken, null)).isEqualTo(403);
        // A teacher can't do admin things.
        assertThat(status("/api/learn/staff/teachers", HttpMethod.GET, dana, null)).isEqualTo(403);
        assertThat(status("/api/learn/staff/children/" + noaId + "/class", HttpMethod.POST, dana, Map.of("classId", 0))).isEqualTo(403);
        assertThat(status("/api/learn/staff/classes/" + rinaClass.get("id"), HttpMethod.PATCH, dana, Map.of("closed", true))).isEqualTo(403);

        // Noa plays addition: always right without a carry, always wrong with one.
        List<Map<String, Object>> answers = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            answers.add(Map.of("questionText", "כמה זה 3" + i + " + 4" + (i % 5) + "?", "chosenAnswer", "1", "correctAnswer", "1",
                    "correct", true, "hintUsed", false, "topic", "ADDITION"));
            answers.add(Map.of("questionText", "כמה זה 38 + 2" + (i + 2) + "?", "chosenAnswer", "5" + i, "correctAnswer", "6" + i,
                    "correct", false, "hintUsed", false, "topic", "ADDITION"));
        }
        assertThat(status("/api/learn/sessions", HttpMethod.POST, noaToken, Map.of("profileId", noaId, "topic", "ADDITION",
                "stage", 3, "mode", "STAGE", "durationMs", 120000, "answers", answers))).isEqualTo(200);

        Map<String, Object> report = get("/api/learn/staff/children/" + noaId + "/report", dana, Map.class);
        List<Map<String, Object>> hard = (List<Map<String, Object>>) report.get("hard");
        List<Map<String, Object>> strong = (List<Map<String, Object>>) report.get("strong");
        assertThat(hard).extracting(m -> m.get("skill")).containsExactly("ADD_CARRY");
        assertThat(hard.get(0).get("example")).isNotNull();
        assertThat(strong).extracting(m -> m.get("skill")).containsExactly("ADD_NO_CARRY");
        assertThat((List<String>) report.get("attention")).anyMatch(a -> a.contains("המרה"));
        assertThat(((Map<String, Object>) report.get("recommendation")).get("text").toString()).contains("חיבור עם המרה");
        assertThat(status("/api/learn/staff/children/" + noaId + "/report", HttpMethod.GET, rina, null)).isEqualTo(403);

        // "האי שלי": a spot opens only when its level is passed; an item is an emoji, never text.
        String island = "/api/learn/profiles/" + noaId + "/island";
        assertThat(status(island, HttpMethod.PUT, noaToken, Map.of("spot", "ADDITION-3", "item", "🌳"))).isEqualTo(409);
        List<Map<String, Object>> allRight = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            allRight.add(Map.of("questionText", "כמה זה 2 + " + i + "?", "chosenAnswer", "1", "correctAnswer", "1",
                    "correct", true, "hintUsed", false, "topic", "ADDITION"));
        }
        assertThat(status("/api/learn/sessions", HttpMethod.POST, noaToken, Map.of("profileId", noaId, "topic", "ADDITION",
                "stage", 1, "mode", "STAGE", "durationMs", 60000, "answers", allRight))).isEqualTo(200);
        assertThat(status(island, HttpMethod.PUT, noaToken, Map.of("spot", "ADDITION-1", "item", "<b>hi</b>"))).isEqualTo(400);
        assertThat(status(island, HttpMethod.PUT, noaToken, Map.of("spot", "ADDITION-1", "item", "🌳"))).isEqualTo(200);
        Map<String, Object> mine = get(island, noaToken, Map.class);
        assertThat(((Map<String, Object>) mine.get("choices")).get("ADDITION-1")).isEqualTo("🌳");
        assertThat((List<Map<String, Object>>) mine.get("earned")).extracting(m -> m.get("spot")).contains("ADDITION-1");
        assertThat(status("/api/learn/profiles/" + noaId + "/island", HttpMethod.GET, dana, null)).isEqualTo(404); // not hers via the child path
        assertThat(status("/api/learn/staff/children/" + noaId + "/island", HttpMethod.GET, dana, null)).isEqualTo(200);
        assertThat(status("/api/learn/staff/children/" + noaId + "/island", HttpMethod.GET, rina, null)).isEqualTo(403);

        // The class table: Noa, with addition marked.
        Map<String, Object> overview = get("/api/learn/staff/overview", dana, Map.class);
        List<Map<String, Object>> rows = (List<Map<String, Object>>) overview.get("children");
        assertThat(rows).hasSize(1);
        assertThat(((Map<String, Object>) rows.get(0).get("topics")).get("ADDITION")).isNotNull();

        // The admin sees everyone, moves Noa to Rina's class - now Rina sees her and Dana doesn't.
        assertThat(get("/api/learn/staff/children", boss, List.class)).hasSize(1);
        assertThat(status("/api/learn/staff/children/" + noaId + "/class", HttpMethod.POST, boss,
                Map.of("classId", rinaClass.get("id")))).isEqualTo(200);
        assertThat(status("/api/learn/staff/children/" + noaId + "/report", HttpMethod.GET, rina, null)).isEqualTo(200);
        assertThat(status("/api/learn/staff/children/" + noaId + "/report", HttpMethod.GET, dana, null)).isEqualTo(403);

        // A closed class takes no new sign-ups. (Shir's own code was used by "boss" above; a 6th sign-up
        // here would hit the public site's limit of 5 per hour per address.)
        assertThat(status("/api/learn/staff/classes/" + danaClass.get("id"), HttpMethod.PATCH, dana, Map.of("closed", true))).isEqualTo(200);
        ResponseEntity<Map> late = http.postForEntity("/api/learn/auth/register", Map.of("joinCode", danaCode,
                "username", "late1", "password", "secret12", "name", "מאוחר", "avatar", "BOY", "grade", "G3"), Map.class);
        assertThat(late.getStatusCode().value()).isEqualTo(403);

        // A blocked teacher is out at once.
        Object danaId = ((List<Map<String, Object>>) get("/api/learn/staff/teachers", boss, List.class)).stream()
                .filter(t -> "dana".equals(t.get("username"))).findFirst().orElseThrow().get("id");
        assertThat(status("/api/learn/staff/teachers/" + danaId + "/disabled", HttpMethod.POST, boss, Map.of("disabled", true))).isEqualTo(200);
        assertThat(status("/api/learn/staff/me", HttpMethod.GET, dana, null)).isIn(401, 403);
    }
}
