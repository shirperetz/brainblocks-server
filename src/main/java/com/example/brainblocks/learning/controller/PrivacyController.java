package com.example.brainblocks.learning.controller;

import com.example.brainblocks.config.LearnerAuthConfig;
import com.example.brainblocks.learning.service.AccountService;
import com.example.brainblocks.learning.service.PrivacyService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;

/** 2026-11-14 (privacy): deleting an account on request, and messages to the site's operator. */
@RestController
@RequestMapping("/api/learn")
public class PrivacyController {

    private final PrivacyService privacy;
    private final AccountService accounts;

    public PrivacyController(PrivacyService privacy, AccountService accounts) {
        this.privacy = privacy;
        this.accounts = accounts;
    }

    private static void own(Long me, Long id) {
        if (!id.equals(me)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }

    private void requireAdmin(Long me) {
        if (!accounts.isAdmin(me)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "רק למנהלת");
    }

    @GetMapping("/profiles/{id}/delete-request")
    public Map<String, Object> deletionState(@RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me, @PathVariable Long id) {
        own(me, id);
        Map<String, Object> m = new HashMap<>();
        m.put("requestedAt", privacy.deletionRequestedAt(id));
        return m;
    }

    /** body: { password } - the account's own password, so a friend borrowing the tablet can't do it. */
    @PostMapping("/profiles/{id}/delete-request")
    public Map<String, Object> requestDeletion(@RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me, @PathVariable Long id,
                                               @RequestBody Map<String, String> body) {
        own(me, id);
        Map<String, Object> m = new HashMap<>();
        m.put("requestedAt", privacy.requestDeletion(id, body.get("password")));
        return m;
    }

    @PostMapping("/profiles/{id}/delete-request/cancel")
    public Map<String, Object> cancelDeletion(@RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me, @PathVariable Long id) {
        own(me, id);
        privacy.cancelDeletion(id);
        return Map.of("ok", true);
    }

    /**
     * Public (no account needed - a parent writes too): { kind, name, reply, text } + the bot trap.
     * A logged-in sender's account is attached when the header carries its token.
     */
    @PostMapping("/auth/contact")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> contact(@RequestBody Map<String, String> body,
                                       @RequestHeader(value = "X-Learner-Token", required = false) String token) {
        LearnController.botCheck(body);
        Long who = token == null || token.isBlank() ? null : accounts.profileFor(token).orElse(null);
        privacy.contact(body.get("kind"), body.get("name"), body.get("reply"), body.get("text"), who);
        return Map.of("ok", true);
    }

    @GetMapping("/admin/privacy")
    public Map<String, Object> adminPrivacy(@RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        requireAdmin(me);
        Map<String, Object> m = new HashMap<>();
        m.put("deletionRequests", privacy.deletionRequests());
        m.put("messages", privacy.messages());
        return m;
    }

    @PostMapping("/admin/profiles/{id}/erase")
    public Map<String, Object> erase(@RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me, @PathVariable Long id) {
        requireAdmin(me);
        if (id.equals(me)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "אי אפשר למחוק את עצמך");
        return Map.of("erased", privacy.erase(id));
    }

    @PostMapping("/admin/profiles/{id}/keep")
    public Map<String, Object> keep(@RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me, @PathVariable Long id) {
        requireAdmin(me);
        privacy.cancelDeletion(id);
        return Map.of("ok", true);
    }

    @PostMapping("/admin/messages/{id}/done")
    public Map<String, Object> done(@RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me, @PathVariable Long id,
                                    @RequestBody(required = false) Map<String, Boolean> body) {
        requireAdmin(me);
        privacy.markDone(id, body == null || !Boolean.FALSE.equals(body.get("done")));
        return Map.of("ok", true);
    }
}
