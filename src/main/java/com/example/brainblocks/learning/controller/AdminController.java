package com.example.brainblocks.learning.controller;

import com.example.brainblocks.config.LearnerAuthConfig;
import com.example.brainblocks.learning.dto.response.ProfileResponse;
import com.example.brainblocks.learning.model.LearnerProfile;
import com.example.brainblocks.learning.repository.LearnerMistakeRepository;
import com.example.brainblocks.learning.repository.LearnerProfileRepository;
import com.example.brainblocks.learning.repository.LearnerSessionRepository;
import com.example.brainblocks.learning.service.AccountService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Shir's admin screen (2026-11-11: "an admin username and password of mine with every permission").
 * Behind the normal device token (LearnerAuthConfig) AND the profile's admin flag, which is set only
 * in the database. Everything here is reversible: block/unblock, new password, join code. No delete.
 */
@RestController
@RequestMapping("/api/learn/admin")
public class AdminController {

    private final AccountService accounts;
    private final LearnerProfileRepository profiles;
    private final LearnerSessionRepository sessions;
    private final LearnerMistakeRepository mistakes;

    public AdminController(AccountService accounts, LearnerProfileRepository profiles,
                           LearnerSessionRepository sessions, LearnerMistakeRepository mistakes) {
        this.accounts = accounts;
        this.profiles = profiles;
        this.sessions = sessions;
        this.mistakes = mistakes;
    }

    private void requireAdmin(Long me) {
        if (!accounts.isAdmin(me)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "רק למנהלת");
        }
    }

    /** Every child, newest activity first. */
    @GetMapping("/children")
    public List<Map<String, Object>> children(@RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        requireAdmin(me);
        return profiles.findAll().stream()
                .sorted(Comparator.comparing(LearnerProfile::getLastActiveAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(p -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", p.getId());
                    m.put("name", p.getName());
                    m.put("username", p.getUsername());
                    m.put("avatar", p.getAvatar());
                    m.put("grade", p.getGrade());
                    m.put("totalPoints", p.getTotalPoints());
                    m.put("lastActiveAt", p.getLastActiveAt());
                    m.put("createdAt", p.getCreatedAt());
                    m.put("admin", p.isAdmin());
                    m.put("disabled", p.isDisabled());
                    m.put("approved", p.isApproved());
                    m.put("sessions", sessions.findByProfileId(p.getId()).size());
                    m.put("openMistakes", mistakes.findByProfileIdAndResolvedFalseOrderByCreatedAtDesc(p.getId()).size());
                    return m;
                })
                .toList();
    }

    /** body: { password, username? } - username only for an old profile without one (or to rename). */
    @PostMapping("/children/{id}/password")
    public ProfileResponse setPassword(@PathVariable Long id, @RequestBody Map<String, String> body,
                                       @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        requireAdmin(me);
        return accounts.setCredentials(id, body.get("username"), body.get("password"));
    }

    /** body: { disabled: true|false } */
    @PostMapping("/children/{id}/disabled")
    public ProfileResponse setDisabled(@PathVariable Long id, @RequestBody Map<String, Boolean> body,
                                       @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        requireAdmin(me);
        return accounts.setDisabled(id, Boolean.TRUE.equals(body.get("disabled")), me);
    }

    /** A child who signed up with the code - let in. (To refuse: block.) */
    @PostMapping("/children/{id}/approve")
    public ProfileResponse approve(@PathVariable Long id, @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        requireAdmin(me);
        return accounts.approve(id);
    }

    @GetMapping("/join-code")
    public Map<String, String> joinCode(@RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        requireAdmin(me);
        return Map.of("code", accounts.joinCode());
    }

    /** body: { code } - blank closes sign-up. */
    @PutMapping("/join-code")
    public Map<String, String> changeJoinCode(@RequestBody Map<String, String> body,
                                              @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        requireAdmin(me);
        return Map.of("code", accounts.changeJoinCode(body.get("code")));
    }
}
