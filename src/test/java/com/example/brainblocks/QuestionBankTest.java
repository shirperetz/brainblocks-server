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
 * 2026-11-12 (Shir: "the exercises are saved in a bank, new ones every time"; stage 2: "check answers on
 * the server, don't reveal the right answer before its time, keep children's data apart"): every served
 * question is a bank row; the secure screen gets no answer; the server judges; a child can't fake "right".
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "DATABASE_URL=jdbc:h2:mem:qbank;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "DATABASE_USER=sa", "DATABASE_PASSWORD=",
        "JOIN_CODE=4711", "ADMIN_USERNAMES=boss", "PUBLIC_ORIGIN=https://example.org",
        "server.port=0"})
@ActiveProfiles("cloud")
class QuestionBankTest {

    @Autowired
    TestRestTemplate http;

    private HttpHeaders as(String token) {
        HttpHeaders h = new HttpHeaders();
        if (token != null) h.set("X-Learner-Token", token);
        return h;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T> T call(String url, HttpMethod m, String token, Object body, Class<T> type) {
        return http.exchange(url, m, new HttpEntity<>(body, as(token)), type).getBody();
    }

    private int status(String url, HttpMethod m, String token, Object body) {
        return http.exchange(url, m, new HttpEntity<>(body, as(token)), String.class).getStatusCode().value();
    }

    @SuppressWarnings("unchecked")
    private Object[] child(String user) {
        Map<String, Object> b = new HashMap<>(Map.of("joinCode", "4711", "username", user, "password", "secret12",
                "name", user, "avatar", "GIRL", "grade", "G3"));
        Map<String, Object> r = call("/api/learn/auth/register", HttpMethod.POST, null, b, Map.class);
        return new Object[] {((Map<String, Object>) r.get("profile")).get("id"), r.get("token")};
    }

    @Test
    @SuppressWarnings("unchecked")
    void theServerJudgesAndHidesTheAnswer() {
        Object[] boss = child("boss");
        Object[] kid = child("kid");
        Object[] other = child("other");
        // children who signed up with the code wait for the admin
        call("/api/learn/staff/children/" + kid[0] + "/approve", HttpMethod.POST, (String) boss[1], null, Map.class);
        call("/api/learn/staff/children/" + other[0] + "/approve", HttpMethod.POST, (String) boss[1], null, Map.class);
        String k = (String) call("/api/learn/auth/login", HttpMethod.POST, null, Map.of("username", "kid", "password", "secret12"), Map.class).get("token");
        String o = (String) call("/api/learn/auth/login", HttpMethod.POST, null, Map.of("username", "other", "password", "secret12"), Map.class).get("token");

        // secure: no right answer, no explanation - but a bank id
        List<Map<String, Object>> qs = call("/api/learn/questions?profileId=" + kid[0] + "&topic=ADDITION&stage=1&count=10&secure=true",
                HttpMethod.GET, k, null, List.class);
        assertThat(qs).hasSize(10);
        for (Map<String, Object> q : qs) {
            assertThat(q.get("correctIndex")).isEqualTo(-1);
            assertThat(q.get("explanation")).isNull();
            assertThat(q.get("bankId")).isNotNull();
        }

        // another child can't answer (or peek through) my question
        Object firstId = qs.get(0).get("bankId");
        assertThat(status("/api/learn/questions/" + firstId + "/answer", HttpMethod.POST, o, Map.of("answer", "1"))).isIn(403, 404);

        // answer each: the first one on purpose wrong, the rest right (the right one is revealed only now)
        List<Map<String, Object>> answers = new ArrayList<>();
        for (int i = 0; i < qs.size(); i++) {
            Map<String, Object> q = qs.get(i);
            List<String> opts = (List<String>) q.get("options");
            Map<String, Object> probe = call("/api/learn/questions/" + q.get("bankId") + "/answer", HttpMethod.POST, k,
                    Map.of("answer", i == 0 ? "no-such-answer" : "?"), Map.class);
            String right = (String) probe.get("correctAnswer");
            assertThat(opts).contains(right);
            assertThat(probe.get("correctIndex")).isEqualTo(opts.indexOf(right));
            if (i > 0) {
                // the first answer counts - "?" was wrong, so a right answer now is only a retry
                assertThat(call("/api/learn/questions/" + q.get("bankId") + "/answer", HttpMethod.POST, k,
                        Map.of("answer", right), Map.class).get("correct")).isEqualTo(true);
            }
            // the phone CLAIMS everything was right
            answers.add(new HashMap<>(Map.of("bankId", q.get("bankId"), "questionText", q.get("text"),
                    "chosenAnswer", right, "correctAnswer", right, "correct", true)));
        }
        Map<String, Object> summary = call("/api/learn/sessions", HttpMethod.POST, k,
                Map.of("profileId", kid[0], "topic", "ADDITION", "stage", 1, "mode", "STAGE", "answers", answers, "durationMs", 60000),
                Map.class);
        // the server judged from the bank: every FIRST answer was wrong -> 0 right, no pass
        assertThat(summary.get("correct")).isEqualTo(0);
        assertThat(summary.get("passed")).isEqualTo(false);
    }
}
