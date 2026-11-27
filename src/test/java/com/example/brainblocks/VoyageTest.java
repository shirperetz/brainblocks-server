package com.example.brainblocks;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.brainblocks.learning.model.Avatar;
import com.example.brainblocks.learning.model.LearnIslandItem;
import com.example.brainblocks.learning.repository.LearnIslandItemRepository;
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

/** 2026-11-27 (idea #7): a ship voyage rewards coins once per place per day, and only with a ship. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "DATABASE_URL=jdbc:h2:mem:voyage;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "DATABASE_USER=sa", "DATABASE_PASSWORD=", "JOIN_CODE=4711", "ADMIN_USERNAMES=boss",
        "PUBLIC_ORIGIN=https://example.org", "ADMIN_2FA=off", "server.port=0"})
@ActiveProfiles("cloud")
class VoyageTest {

    @Autowired TestRestTemplate http;
    @Autowired AccountService accounts;
    @Autowired LearnerProfileRepository profiles;
    @Autowired LearnIslandItemRepository islandItems;

    private ResponseEntity<Map> call(HttpMethod m, String path, String token) {
        HttpHeaders h = new HttpHeaders();
        h.add("Origin", "https://example.org");
        if (token != null) h.add("X-Learner-Token", token);
        return http.exchange(path, m, new HttpEntity<>(h), Map.class);
    }

    @Test
    void aShipIsNeededAndEachPlaceRewardsOncePerDay() {
        var s = accounts.register("4711", "sailor", "pass12345", "ים", Avatar.values()[0].name(), "G3", "MEDIUM");
        Long id = s.profile().getId();
        var p = profiles.findById(id).orElseThrow();
        p.setApproved(true);
        profiles.save(p);
        String t = accounts.login("sailor", "pass12345", java.time.Instant.now()).token();

        // no ship yet
        assertThat(call(HttpMethod.GET, "/api/learn/profiles/" + id + "/voyages", t).getBody().get("hasShip")).isEqualTo(false);
        assertThat(call(HttpMethod.POST, "/api/learn/profiles/" + id + "/voyages/reef/reward", t).getStatusCode().value()).isEqualTo(403);

        islandItems.save(new LearnIslandItem(id, "ship", 1, 1));
        var list = call(HttpMethod.GET, "/api/learn/profiles/" + id + "/voyages", t).getBody();
        assertThat(list.get("hasShip")).isEqualTo(true);
        assertThat((List<?>) list.get("destinations")).isNotEmpty();

        var first = call(HttpMethod.POST, "/api/learn/profiles/" + id + "/voyages/reef/reward", t).getBody();
        assertThat(((Number) first.get("coins")).intValue()).isEqualTo(10);
        assertThat(first.get("already")).isEqualTo(false);
        // same place again today = no extra coins
        var again = call(HttpMethod.POST, "/api/learn/profiles/" + id + "/voyages/reef/reward", t).getBody();
        assertThat(again.get("already")).isEqualTo(true);
        assertThat(((Number) again.get("coins")).intValue()).isZero();
        // another place still rewards
        assertThat(((Number) call(HttpMethod.POST, "/api/learn/profiles/" + id + "/voyages/volcano/reward", t).getBody().get("coins")).intValue()).isEqualTo(20);
        // unknown place
        assertThat(call(HttpMethod.POST, "/api/learn/profiles/" + id + "/voyages/nowhere/reward", t).getStatusCode().value()).isEqualTo(404);
        // reef now shows done today
        var list2 = call(HttpMethod.GET, "/api/learn/profiles/" + id + "/voyages", t).getBody();
        var reef = ((List<Map>) list2.get("destinations")).stream().filter(d -> d.get("key").equals("reef")).findFirst().orElseThrow();
        assertThat(reef.get("doneToday")).isEqualTo(true);
    }
}
