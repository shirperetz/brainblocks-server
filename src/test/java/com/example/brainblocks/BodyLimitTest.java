package com.example.brainblocks;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

/** 2026-11-24 security review: a huge request body is refused before it is read; normal ones pass. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "DATABASE_URL=jdbc:h2:mem:bodylimit;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "DATABASE_USER=sa", "DATABASE_PASSWORD=", "JOIN_CODE=4711", "ADMIN_USERNAMES=boss",
        "PUBLIC_ORIGIN=https://example.org", "server.port=0"})
@ActiveProfiles("cloud")
class BodyLimitTest {
    @Autowired TestRestTemplate http;

    @Test void aHugeBodyIsRefusedANormalOneIsNot() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        String huge = "{\"username\":\"" + "a".repeat(200_000) + "\",\"password\":\"x\"}";
        assertThat(http.postForEntity("/api/learn/auth/login", new HttpEntity<>(huge, h), String.class).getStatusCode().value()).isIn(400, 413);
        assertThat(http.postForEntity("/api/learn/auth/login", new HttpEntity<>(Map.of("username", "nobody", "password", "wrong-pass"), h), String.class)
                .getStatusCode().value()).isEqualTo(401);
    }
}
