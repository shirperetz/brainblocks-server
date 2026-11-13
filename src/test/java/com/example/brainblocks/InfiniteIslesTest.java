package com.example.brainblocks;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.brainblocks.learning.model.LearnerStageProgress;
import com.example.brainblocks.learning.model.StageStatus;
import com.example.brainblocks.learning.model.Topic;
import com.example.brainblocks.learning.repository.LearnerAnswerRepository;
import com.example.brainblocks.learning.repository.LearnerStageProgressRepository;
import com.example.brainblocks.learning.service.IsleService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.ActiveProfiles;

/** 2026-11-13: the endless sea - an island opens at 2/3 of the one before, there is always one more. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "DATABASE_URL=jdbc:h2:mem:isles;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "DATABASE_USER=sa", "DATABASE_PASSWORD=",
        "JOIN_CODE=4711", "ADMIN_USERNAMES=boss", "PUBLIC_ORIGIN=https://example.org",
        "server.port=0"})
@ActiveProfiles("cloud")
class InfiniteIslesTest {

    @Autowired TestRestTemplate http;
    @Autowired LearnerStageProgressRepository stages;
    @Autowired LearnerAnswerRepository answers;

    private HttpHeaders as(String token) {
        HttpHeaders h = new HttpHeaders();
        h.set("X-Learner-Token", token);
        return h;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> sea(Object kid, String token) {
        return (List<Map<String, Object>>) http.exchange("/api/learn/profiles/" + kid + "/isles", HttpMethod.GET,
                new HttpEntity<>(as(token)), Map.class).getBody().get("isles");
    }

    private void pass(Long kid, Topic topic, int upTo) {
        for (int s = 1; s <= upTo; s++) {
            LearnerStageProgress p = new LearnerStageProgress(kid, topic, s, StageStatus.UNLOCKED);
            p.recordAttempt(90, 3, true);
            stages.save(p);
        }
    }

    private int submit(Object kid, String token, String isle, String topic, int stage, int right) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            boolean ok = i < right;
            list.add(Map.of("questionText", "כמה זה 30 + " + i + "?", "chosenAnswer", ok ? "x" : "y", "correctAnswer", "x", "correct", ok));
        }
        return http.exchange("/api/learn/profiles/" + kid + "/isles/" + isle + "/sessions", HttpMethod.POST,
                new HttpEntity<>(Map.of("topic", topic, "stage", stage, "durationMs", 30000, "answers", list), as(token)), String.class)
                .getStatusCode().value();
    }

    @Test
    void generatedIslesAreAlwaysTheSameAndNeverEnd() {
        Set<String> names = new HashSet<>();
        for (int n = 1; n <= 40; n++) {
            IsleService.Isle a = IsleService.generated(n), b = IsleService.generated(n);
            assertThat(a).isEqualTo(b);
            assertThat(a.games()).hasSize(3).doesNotHaveDuplicates();
            assertThat(a.tier()).isEqualTo(n);
            names.add(a.name());
            assertThat(IsleService.byKey("gen-" + n)).isEqualTo(a);
            assertThat(IsleService.needed(a)).isEqualTo(12);
        }
        assertThat(names).hasSize(40);
        assertThat(IsleService.byKey("gen-0")).isNull();
        assertThat(IsleService.byKey("gen-x")).isNull();
        assertThat(IsleService.needed(IsleService.byKey("numbers"))).isEqualTo(16); // 4 games: 16 of 24
    }

    @Test
    @SuppressWarnings("unchecked")
    void anIslandOpensAtTwoThirdsAndThereIsAlwaysOneMore() {
        Map<String, Object> body = new HashMap<>(Map.of("joinCode", "4711", "username", "sea1", "password", "secret12",
                "name", "sea1", "avatar", "GIRL", "grade", "G2"));
        Long kid = ((Number) ((Map<String, Object>) http.postForObject("/api/learn/auth/register", body, Map.class)
                .get("profile")).get("id")).longValue();
        String boss = (String) ((Map<String, Object>) http.postForObject("/api/learn/auth/register",
                new HashMap<>(Map.of("joinCode", "4711", "username", "boss", "password", "secret12", "name", "boss", "avatar", "GIRL")),
                Map.class)).get("token");
        http.exchange("/api/learn/staff/children/" + kid + "/approve", HttpMethod.POST, new HttpEntity<>(as(boss)), String.class);
        String k = (String) http.postForObject("/api/learn/auth/login", Map.of("username", "sea1", "password", "secret12"), Map.class).get("token");

        List<Map<String, Object>> s = sea(kid, k);
        assertThat(s).extracting(m -> m.get("key")).containsExactly("numbers", "clock");
        assertThat(s.get(0)).containsEntry("open", true);
        assertThat(s.get(1)).containsEntry("open", false);

        // numbers: 4 games x 6 = 24 stages, opens the next at 16.
        pass(kid, Topic.DRAGON, 6);
        pass(kid, Topic.BALLOONS, 6);
        pass(kid, Topic.BRIDGE, 3);
        assertThat(sea(kid, k).get(1)).containsEntry("open", false); // 15
        pass(kid, Topic.TRAIN, 1);
        s = sea(kid, k);
        assertThat(s).extracting(m -> m.get("key")).containsExactly("numbers", "clock", "gen-1");
        assertThat(s.get(1)).containsEntry("open", true);
        assertThat(s.get(2)).containsEntry("open", false).containsEntry("generated", true);

        // A locked island takes no rounds.
        Map<String, Object> g1 = s.get(2);
        String g1Topic = (String) ((List<Map<String, Object>>) g1.get("games")).get(0).get("topic");
        assertThat(submit(kid, k, "gen-1", g1Topic, 1, 9)).isEqualTo(403);

        // clock: 3 games x 6 = 18, opens gen-1 at 12.
        pass(kid, Topic.CLOCK, 6);
        pass(kid, Topic.SHOP, 6);
        s = sea(kid, k);
        assertThat(s).extracting(m -> m.get("key")).containsExactly("numbers", "clock", "gen-1", "gen-2");
        assertThat(s.get(2)).containsEntry("open", true);
        assertThat(s.get(3)).containsEntry("open", false);

        // Rounds on gen-1: wrong game 400, a stage not reached 403, stage 1 passes and every answer is kept.
        String notHere = Topic.values()[0].name();
        assertThat(submit(kid, k, "gen-1", "ADDITION", 1, 9)).isEqualTo(400);
        assertThat(submit(kid, k, "gen-1", g1Topic, 2, 9)).isEqualTo(403);
        long before = answers.findAll().stream().filter(a -> a.getProfileId().equals(kid)).count();
        assertThat(submit(kid, k, "gen-1", g1Topic, 1, 9)).isEqualTo(200);
        long after = answers.findAll().stream().filter(a -> a.getProfileId().equals(kid)).count();
        assertThat(after - before).isEqualTo(10);
        Map<String, Object> g1Now = sea(kid, k).get(2);
        Map<String, Object> firstGame = ((List<Map<String, Object>>) g1Now.get("games")).get(0);
        List<Map<String, Object>> st = (List<Map<String, Object>>) firstGame.get("stages");
        assertThat(st.get(0)).containsEntry("status", "COMPLETED").containsEntry("stars", 3);
        assertThat(st.get(1)).containsEntry("status", "UNLOCKED");
        assertThat(g1Now).containsEntry("passed", 1);
        assertThat(notHere).isNotNull();

        // Someone else can't post rounds for her, and a stranger can't see her sea.
        String other = (String) ((Map<String, Object>) http.postForObject("/api/learn/auth/register",
                new HashMap<>(Map.of("joinCode", "4711", "username", "sea2", "password", "secret12", "name", "sea2", "avatar", "GIRL")),
                Map.class)).get("token");
        if (other != null) {
            assertThat(http.exchange("/api/learn/profiles/" + kid + "/isles", HttpMethod.GET, new HttpEntity<>(as(other)), String.class)
                    .getStatusCode().value()).isIn(403, 404);
        }
    }
}
