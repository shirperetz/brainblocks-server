package com.example.brainblocks;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.brainblocks.learning.model.LearnerStageProgress;
import com.example.brainblocks.learning.model.StageStatus;
import com.example.brainblocks.learning.model.Topic;
import com.example.brainblocks.learning.repository.LearnerStageProgressRepository;
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
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/** 2026-11-13: the 3D island shop - a coin per star, the server checks every price and place. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "DATABASE_URL=jdbc:h2:mem:shop;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "DATABASE_USER=sa", "DATABASE_PASSWORD=",
        "JOIN_CODE=4711", "ADMIN_USERNAMES=boss", "PUBLIC_ORIGIN=https://example.org",
        "server.port=0"})
@ActiveProfiles("cloud")
class IslandShopTest {

    @Autowired TestRestTemplate http;
    @Autowired LearnerStageProgressRepository stages;

    private HttpHeaders as(String token) {
        HttpHeaders h = new HttpHeaders();
        h.set("X-Learner-Token", token);
        return h;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> register(String user, String boss) {
        Map<String, Object> body = new HashMap<>(Map.of("joinCode", "4711", "username", user, "password", "secret12",
                "name", user, "avatar", "GIRL", "grade", "G2"));
        Map<String, Object> r = http.postForObject("/api/learn/auth/register", body, Map.class);
        Long id = ((Number) ((Map<String, Object>) r.get("profile")).get("id")).longValue();
        if (boss != null) http.exchange("/api/learn/staff/children/" + id + "/approve", HttpMethod.POST, new HttpEntity<>(as(boss)), String.class);
        String token = (String) http.postForObject("/api/learn/auth/login", Map.of("username", user, "password", "secret12"), Map.class).get("token");
        return Map.of("id", id, "token", token);
    }

    private void stars(Long kid, Topic t, int stagesCount, int each) {
        for (int s = 1; s <= stagesCount; s++) {
            LearnerStageProgress p = new LearnerStageProgress(kid, t, s, StageStatus.UNLOCKED);
            p.recordAttempt(95, each, true);
            stages.save(p);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ResponseEntity<Map> post(Object kid, String token, String what, Map<String, Object> body) {
        return http.exchange("/api/learn/profiles/" + kid + "/my-island/" + what, HttpMethod.POST,
                new HttpEntity<>(body, as(token)), Map.class);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Map<String, Object> state(Object kid, String token) {
        return http.exchange("/api/learn/profiles/" + kid + "/my-island", HttpMethod.GET, new HttpEntity<>(as(token)), Map.class).getBody();
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void coinsBuyBuildUpgradeLandAndChest() {
        String boss = (String) ((Map<String, Object>) http.postForObject("/api/learn/auth/register",
                new HashMap<>(Map.of("joinCode", "4711", "username", "boss", "password", "secret12", "name", "boss", "avatar", "GIRL")),
                Map.class)).get("token");
        Map<String, Object> a = register("shopkid", boss);
        Long kid = (Long) a.get("id");
        String k = (String) a.get("token");

        // 6 stages x 3 stars = 18 coins
        stars(kid, Topic.DRAGON, 6, 3);
        Map<String, Object> s = state(kid, k);
        assertThat(s.get("stars")).isEqualTo(18);
        assertThat(s.get("coins")).isEqualTo(18);
        assertThat(s.get("openSlots")).isEqualTo(8);
        assertThat((List<?>) s.get("catalog")).isNotEmpty();

        // a tent (10) on slot 0 -> 8 left
        ResponseEntity<Map> r = post(kid, k, "build", Map.of("slot", 0, "kind", "house"));
        assertThat(r.getStatusCode().value()).isEqualTo(200);
        assertThat(r.getBody().get("coins")).isEqualTo(8);
        // the same place again, a place not open yet, something unknown, too expensive
        assertThat(post(kid, k, "build", Map.of("slot", 0, "kind", "tree")).getStatusCode().value()).isEqualTo(400);
        assertThat(post(kid, k, "build", Map.of("slot", 9, "kind", "tree")).getStatusCode().value()).isEqualTo(400);
        assertThat(post(kid, k, "build", Map.of("slot", 1, "kind", "castle")).getStatusCode().value()).isEqualTo(400);
        assertThat(post(kid, k, "build", Map.of("slot", 1, "kind", "lighthouse")).getStatusCode().value()).isEqualTo(402);
        // upgrading costs 30: not yet
        assertThat(post(kid, k, "upgrade", Map.of("slot", 0)).getStatusCode().value()).isEqualTo(402);

        // more stars -> upgrade to level 2, land opens 2 more places
        stars(kid, Topic.BALLOONS, 6, 3);
        stars(kid, Topic.BRIDGE, 6, 3);
        stars(kid, Topic.TRAIN, 6, 3); // 72 stars total, 62 left
        r = post(kid, k, "upgrade", Map.of("slot", 0));
        assertThat(r.getStatusCode().value()).isEqualTo(200);
        assertThat(r.getBody().get("coins")).isEqualTo(32);
        assertThat(((List<Map<String, Object>>) r.getBody().get("buildings")).get(0)).containsEntry("level", 2);
        r = post(kid, k, "land", Map.of());
        assertThat(r.getBody()).containsEntry("openSlots", 10).containsEntry("coins", 7);
        assertThat(post(kid, k, "build", Map.of("slot", 9, "kind", "flowers")).getStatusCode().value()).isEqualTo(200);

        // finishing the numbers island (16 of 24) gave a treasure chest: +20, once
        List<Map<String, Object>> chests = (List<Map<String, Object>>) state(kid, k).get("chests");
        assertThat(chests).extracting(c -> c.get("isleKey")).contains("numbers");
        r = post(kid, k, "chest", Map.of("isleKey", "numbers"));
        assertThat(r.getBody()).containsEntry("opened", 20).containsEntry("coins", 23);
        assertThat(post(kid, k, "chest", Map.of("isleKey", "numbers")).getStatusCode().value()).isEqualTo(400);

        // another child can't build on her island or see it
        Map<String, Object> b = register("shopkid2", boss);
        assertThat(post(kid, (String) b.get("token"), "build", Map.of("slot", 2, "kind", "tree")).getStatusCode().value()).isEqualTo(404);
        assertThat(http.exchange("/api/learn/profiles/" + kid + "/my-island", HttpMethod.GET, new HttpEntity<>(as((String) b.get("token"))), String.class)
                .getStatusCode().value()).isIn(403, 404);
    }
}
