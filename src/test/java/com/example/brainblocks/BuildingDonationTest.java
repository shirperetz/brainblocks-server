package com.example.brainblocks;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.brainblocks.learning.model.LearnIslandItem;
import com.example.brainblocks.learning.repository.LearnIslandItemRepository;
import java.util.HashMap;
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
 * 2026-11-14 (A5, Shir: "donate coins to the class building"): coins leave the island wallet and become
 * bricks (2 coins = 1 brick), at most 20 coins a week per child, never more than the child has.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "DATABASE_URL=jdbc:h2:mem:donate;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "DATABASE_USER=sa", "DATABASE_PASSWORD=",
        "JOIN_CODE=4711", "ADMIN_USERNAMES=boss", "PUBLIC_ORIGIN=https://example.org",
        "server.port=0"})
@ActiveProfiles("cloud")
class BuildingDonationTest {

    @Autowired TestRestTemplate http;
    @Autowired LearnIslandItemRepository items;

    private static HttpHeaders h(String token) {
        HttpHeaders h = new HttpHeaders();
        h.set("X-Learner-Token", token);
        return h;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> call(HttpMethod m, String path, Object body, String token, int expect) {
        var r = http.exchange(path, m, new HttpEntity<>(body, h(token)), Map.class);
        assertThat(r.getStatusCode().value()).as(m + " " + path + " " + body).isEqualTo(expect);
        return r.getBody();
    }

    private int coins(Long id, String token) {
        return ((Number) call(HttpMethod.GET, "/api/learn/profiles/" + id + "/my-island", null, token, 200).get("coins")).intValue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void coinsBecomeBricks() {
        String boss = (String) http.postForObject("/api/learn/auth/register", new HashMap<>(Map.of("joinCode", "4711",
                "username", "boss", "password", "secret12", "name", "boss", "avatar", "GIRL")), Map.class).get("token");
        Map<String, Object> cls = call(HttpMethod.POST, "/api/learn/staff/classes", Map.of("name", "ג׳1"), boss, 200);
        Map<String, Object> kid = http.postForObject("/api/learn/auth/register", new HashMap<>(Map.of("joinCode", cls.get("joinCode"),
                "username", "kidd", "password", "secret12", "name", "kidd", "avatar", "GIRL", "grade", "G3")), Map.class);
        Long kidId = ((Number) ((Map<String, Object>) kid.get("profile")).get("id")).longValue();
        call(HttpMethod.POST, "/api/learn/staff/children/" + kidId + "/approve", null, boss, 200);
        String k = (String) http.postForObject("/api/learn/auth/login", Map.of("username", "kidd", "password", "secret12"), Map.class).get("token");
        String path = "/api/learn/profiles/" + kidId + "/class-building/donate";

        // no coins yet -> refused
        call(HttpMethod.POST, path, Map.of("coins", 4), k, 400);
        items.save(new LearnIslandItem(kidId, "bonus", -1, 100));
        assertThat(coins(kidId, k)).isEqualTo(100);

        Map<String, Object> b = call(HttpMethod.GET, "/api/learn/profiles/" + kidId + "/class-building", null, k, 200);
        assertThat(b).containsEntry("target", 30).containsEntry("built", 0).containsEntry("canDonate", 20);

        call(HttpMethod.POST, path, Map.of("coins", 3), k, 400);   // pairs only
        b = call(HttpMethod.POST, path, Map.of("coins", 4), k, 200);
        assertThat(b).containsEntry("built", 2).containsEntry("myDonated", 4).containsEntry("canDonate", 16).containsEntry("coins", 96);
        call(HttpMethod.POST, path, Map.of("coins", 18), k, 400);  // only 16 left this week
        b = call(HttpMethod.POST, path, Map.of("coins", 16), k, 200);
        assertThat(b).containsEntry("built", 10).containsEntry("canDonate", 0).containsEntry("donatedCoins", 20);
        call(HttpMethod.POST, path, Map.of("coins", 2), k, 400);   // the week's limit
        assertThat(coins(kidId, k)).isEqualTo(80);
        // the donation is not something on the island
        assertThat((java.util.List<Object>) call(HttpMethod.GET, "/api/learn/profiles/" + kidId + "/my-island", null, k, 200).get("buildings")).isEmpty();

        // the teacher sees the bricks too; another child can't donate for this one
        Map<String, Object> tb = call(HttpMethod.GET, "/api/learn/staff/classes/" + cls.get("id") + "/building", null, boss, 200);
        assertThat(tb).containsEntry("built", 10).containsEntry("donatedCoins", 20);
        call(HttpMethod.POST, path, Map.of("coins", 2), boss, 404);
    }
}
