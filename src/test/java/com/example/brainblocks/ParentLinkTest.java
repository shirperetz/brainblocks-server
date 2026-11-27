package com.example.brainblocks;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.brainblocks.learning.model.Avatar;
import com.example.brainblocks.learning.repository.LearnParentLinkRepository;
import com.example.brainblocks.learning.repository.LearnerProfileRepository;
import com.example.brainblocks.learning.service.AccountService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

/** 2026-11-26 (idea #9): a teacher's read-only link for parents - works without log-in, only for that child, can be cancelled. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "DATABASE_URL=jdbc:h2:mem:parentlink;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "DATABASE_USER=sa", "DATABASE_PASSWORD=", "JOIN_CODE=4711", "ADMIN_USERNAMES=boss",
        "PUBLIC_ORIGIN=https://example.org", "ADMIN_2FA=off", "server.port=0"})
@ActiveProfiles("cloud")
class ParentLinkTest {

    @Autowired TestRestTemplate http;
    @Autowired AccountService accounts;
    @Autowired LearnerProfileRepository profiles;
    @Autowired LearnParentLinkRepository links;

    private ResponseEntity<Map> call(HttpMethod m, String path, String token) {
        HttpHeaders h = new HttpHeaders();
        h.add("Origin", "https://example.org");
        if (token != null) h.add("X-Learner-Token", token);
        return http.exchange(path, m, new HttpEntity<>(h), Map.class);
    }

    @Test
    void teacherMakesALinkParentsSeeOnlyTheFriendlyPartsAndItCanBeCancelled() {
        String boss = accounts.register("4711", "boss", "pass1234567", "שיר", Avatar.values()[0].name(), "G5", "MEDIUM").token();
        var kidS = accounts.register("4711", "plkid", "pass12345", "נועה כהן", Avatar.values()[0].name(), "G3", "MEDIUM");
        Long kid = kidS.profile().getId();
        var p = profiles.findById(kid).orElseThrow();
        p.setApproved(true);
        profiles.save(p);
        String kidToken = accounts.login("plkid", "pass12345", java.time.Instant.now()).token();

        // a child can't make a link; nobody without log-in
        assertThat(call(HttpMethod.POST, "/api/learn/staff/children/" + kid + "/parent-link", kidToken).getStatusCode().value()).isEqualTo(403);
        assertThat(call(HttpMethod.POST, "/api/learn/staff/children/" + kid + "/parent-link", null).getStatusCode().value()).isEqualTo(401);

        var made = call(HttpMethod.POST, "/api/learn/staff/children/" + kid + "/parent-link", boss);
        assertThat(made.getStatusCode().value()).isEqualTo(200);
        String code = (String) made.getBody().get("code");
        assertThat(links.findAll()).allMatch(l -> !l.getCodeHash().equals(code));

        var view = call(HttpMethod.GET, "/api/learn/parent-view/" + code, null);
        assertThat(view.getStatusCode().value()).isEqualTo(200);
        assertThat(view.getBody().get("name")).isEqualTo("נועה");
        assertThat(view.getBody()).containsKeys("week", "strong", "hard", "recommendation").doesNotContainKeys("username", "child", "classId");
        assertThat(view.getBody().toString()).doesNotContain("plkid");

        assertThat(call(HttpMethod.GET, "/api/learn/parent-view/" + code + "x", null).getStatusCode().value()).isEqualTo(404);
        // a new link replaces the old one; cancelling ends it
        String code2 = (String) call(HttpMethod.POST, "/api/learn/staff/children/" + kid + "/parent-link", boss).getBody().get("code");
        assertThat(call(HttpMethod.GET, "/api/learn/parent-view/" + code, null).getStatusCode().value()).isEqualTo(404);
        assertThat(call(HttpMethod.GET, "/api/learn/parent-view/" + code2, null).getStatusCode().value()).isEqualTo(200);
        assertThat(call(HttpMethod.GET, "/api/learn/staff/children/" + kid + "/parent-link", boss).getBody().get("active")).isEqualTo(true);
        call(HttpMethod.DELETE, "/api/learn/staff/children/" + kid + "/parent-link", boss);
        assertThat(call(HttpMethod.GET, "/api/learn/parent-view/" + code2, null).getStatusCode().value()).isEqualTo(404);
    }
}
