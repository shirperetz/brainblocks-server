package com.example.brainblocks;

import static org.junit.jupiter.api.Assertions.*;

import com.example.brainblocks.learning.model.Avatar;
import com.example.brainblocks.learning.model.LearnerProfile;
import com.example.brainblocks.learning.repository.LearnTokenRepository;
import com.example.brainblocks.learning.repository.LearnerProfileRepository;
import com.example.brainblocks.learning.service.AccountService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 2026-11-26 (security #14): the database keeps only SHA-256 of device tokens; every device has its own,
 * logging out one device leaves the others; a token from before the change still works (once moved).
 */
@SpringBootTest(properties = {
        "DATABASE_URL=jdbc:h2:mem:tokenhash;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "DATABASE_USER=sa", "DATABASE_PASSWORD=", "JOIN_CODE=4711", "ADMIN_USERNAMES=boss", "PUBLIC_ORIGIN=https://example.org"})
@ActiveProfiles("cloud")
class TokenHashTest {

    @Autowired AccountService accounts;
    @Autowired LearnerProfileRepository profiles;
    @Autowired LearnTokenRepository tokens;

    private LearnerProfile kid(String user) {
        var s = accounts.register("4711", user, "pass12345", "ילד", Avatar.values()[0].name(), "G3", "MEDIUM");
        LearnerProfile p = profiles.findById(s.profile().getId()).orElseThrow();
        p.setApproved(true);
        return profiles.save(p);
    }

    @Test
    void tokensAreHashedPerDeviceAndLogoutIsPerDevice() {
        LearnerProfile p = kid("tokkid1");
        String phone = accounts.login("tokkid1", "pass12345", Instant.now()).token();
        String tablet = accounts.login("tokkid1", "pass12345", Instant.now()).token();
        assertNotEquals(phone, tablet);
        assertEquals(p.getId(), accounts.profileFor(phone).orElseThrow());
        assertEquals(p.getId(), accounts.profileFor(tablet).orElseThrow());
        // nothing in the database is the token itself
        assertNull(profiles.findById(p.getId()).orElseThrow().getAuthToken());
        tokens.findAll().forEach(t -> {
            assertNotEquals(phone, t.getTokenHash());
            assertNotEquals(tablet, t.getTokenHash());
            assertEquals(64, t.getTokenHash().length());
        });
        accounts.logout(phone);
        assertTrue(accounts.profileFor(phone).isEmpty());
        assertEquals(p.getId(), accounts.profileFor(tablet).orElseThrow());
        // blocking ends every device
        accounts.setDisabled(p.getId(), true, -1L);
        assertTrue(accounts.profileFor(tablet).isEmpty());
        assertEquals(0, tokens.findByProfileIdOrderByUsedAtDesc(p.getId()).size());
    }

    @Test
    void anOldTokenStillWorksAndMovesIntoAHash() {
        LearnerProfile p = kid("tokkid2");
        p.setAuthToken("old-token-from-before-the-change-1234567890");
        profiles.save(p);
        assertEquals(p.getId(), accounts.profileFor("old-token-from-before-the-change-1234567890").orElseThrow());
        assertNull(profiles.findById(p.getId()).orElseThrow().getAuthToken());
        assertEquals(1, tokens.findByProfileIdOrderByUsedAtDesc(p.getId()).size());
        assertEquals(p.getId(), accounts.profileFor("old-token-from-before-the-change-1234567890").orElseThrow());
        assertTrue(accounts.profileFor("not-a-token").isEmpty());
    }

    @Test
    void atMostTenDevices() {
        LearnerProfile p = kid("tokkid3");
        String first = accounts.login("tokkid3", "pass12345", Instant.now()).token();
        for (int i = 0; i < 11; i++) accounts.login("tokkid3", "pass12345", Instant.now().plusSeconds(i + 1));
        assertEquals(10, tokens.findByProfileIdOrderByUsedAtDesc(p.getId()).size());
        assertTrue(accounts.profileFor(first).isEmpty());
    }
}
