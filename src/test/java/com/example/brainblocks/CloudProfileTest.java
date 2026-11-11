package com.example.brainblocks;

import static org.assertj.core.api.Assertions.assertThat;

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
 * 2026-11-11: the public site's configuration (profile "cloud") boots and works end to end - on an
 * in-memory database in PostgreSQL mode instead of the real (Neon) one. Nothing from the PC is used:
 * no local.properties, no MySQL, no local AI.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "DATABASE_URL=jdbc:h2:mem:cloud;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "DATABASE_USER=sa", "DATABASE_PASSWORD=",
        "JOIN_CODE=4711", "ADMIN_USERNAMES=boss", "PUBLIC_ORIGIN=https://example.org",
        "server.port=0"})
@ActiveProfiles("cloud")
class CloudProfileTest {

    @Autowired
    TestRestTemplate http;

    @Test
    @SuppressWarnings("unchecked")
    void theSiteWorksOnItsOwnEmptyDatabase() {
        // The admin username (from the host's settings) is in right away - no one to approve her.
        Map<String, Object> boss = http.postForObject("/api/learn/auth/register", Map.of("joinCode", "4711",
                "username", "boss", "password", "secret12", "name", "שיר", "avatar", "GIRL", "grade", "G6"), Map.class);
        assertThat(boss.get("token")).isNotNull();
        assertThat(((Map<String, Object>) boss.get("profile")).get("admin")).isEqualTo(true);
        HttpHeaders admin = new HttpHeaders();
        admin.set("X-Learner-Token", (String) boss.get("token"));

        // A child: wrong code refused; right code -> waits for approval.
        ResponseEntity<Map> wrong = http.postForEntity("/api/learn/auth/register", Map.of("joinCode", "0000",
                "username", "noa", "password", "1234", "name", "נועה", "avatar", "ROBOT"), Map.class);
        assertThat(wrong.getStatusCode().value()).isEqualTo(403);
        Map<String, Object> noa = http.postForObject("/api/learn/auth/register", Map.of("joinCode", "4711",
                "username", "noa", "password", "1234", "name", "נועה", "avatar", "ROBOT", "grade", "G5"), Map.class);
        assertThat(noa.get("token")).isNull();
        assertThat(http.postForEntity("/api/learn/auth/login", Map.of("username", "noa", "password", "1234"), Map.class)
                .getStatusCode().value()).isEqualTo(403);

        // The admin approves; the child logs in and gets questions.
        ResponseEntity<List> kids = http.exchange("/api/learn/admin/children", HttpMethod.GET, new HttpEntity<>(admin), List.class);
        Object noaId = ((List<Map<String, Object>>) kids.getBody()).stream()
                .filter(k -> "noa".equals(k.get("username"))).findFirst().orElseThrow().get("id");
        assertThat(http.exchange("/api/learn/admin/children/" + noaId + "/approve", HttpMethod.POST, new HttpEntity<>(admin), Map.class)
                .getStatusCode().value()).isEqualTo(200);
        Map<String, Object> in = http.postForObject("/api/learn/auth/login", Map.of("username", "noa", "password", "1234"), Map.class);
        HttpHeaders child = new HttpHeaders();
        child.set("X-Learner-Token", (String) in.get("token"));
        ResponseEntity<List> qs = http.exchange("/api/learn/questions?profileId=" + noaId + "&topic=FRACTIONS&stage=1",
                HttpMethod.GET, new HttpEntity<>(child), List.class);
        assertThat(qs.getStatusCode().value()).isEqualTo(200);
        assertThat(qs.getBody()).hasSize(10);

        // "לא הבנתי" works without the local AI (explanations from code), the voice is simply absent.
        Map<String, Object> help = http.exchange("/api/learn/help", HttpMethod.POST, new HttpEntity<>(Map.of(
                "questionText", "כמה זה 7 + 5?", "correctAnswer", "12", "chosenAnswer", "11", "grade", "G1", "attempt", 2), child), Map.class).getBody();
        assertThat((String) help.get("text")).isNotBlank().doesNotContain("12");
        assertThat(http.exchange("/api/learn/speak", HttpMethod.POST, new HttpEntity<>(Map.of("text", "שלום"), child), byte[].class)
                .getStatusCode().value()).isEqualTo(204);
    }
}
