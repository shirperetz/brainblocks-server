package com.example.brainblocks.learning.controller;

import com.example.brainblocks.config.LearnerAuthConfig;
import com.example.brainblocks.learning.model.LearnParentLink;
import com.example.brainblocks.learning.model.LearnerProfile;
import com.example.brainblocks.learning.repository.LearnParentLinkRepository;
import com.example.brainblocks.learning.repository.LearnerProfileRepository;
import com.example.brainblocks.learning.service.AccessService;
import com.example.brainblocks.learning.service.ReportService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * 2026-11-26 (Shir: idea #9 - a report for parents). The teacher (or Shir) makes a link for one child
 * and sends it home; whoever has the link sees a short, read-only weekly picture (no log-in, no names of
 * other children). One active link per child, 30 days, can be cancelled. Only the code's hash is stored.
 */
@RestController
public class ParentLinkController {

    static final int DAYS = 30;

    private final AccessService access;
    private final ReportService reports;
    private final LearnParentLinkRepository links;
    private final LearnerProfileRepository profiles;
    private final SecureRandom random = new SecureRandom();

    public ParentLinkController(AccessService access, ReportService reports, LearnParentLinkRepository links,
                                LearnerProfileRepository profiles) {
        this.access = access;
        this.reports = reports;
        this.links = links;
        this.profiles = profiles;
    }

    @GetMapping("/api/learn/staff/children/{id}/parent-link")
    public Map<String, Object> status(@PathVariable Long id, @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        access.requireStaff(me);
        access.requireCanSee(me, id);
        Map<String, Object> out = new LinkedHashMap<>();
        links.findByProfileId(id).stream().filter(l -> l.getExpiresAt().isAfter(LocalDateTime.now())).findFirst().ifPresentOrElse(l -> {
            out.put("active", true);
            out.put("expiresAt", l.getExpiresAt().toString());
            out.put("lastViewedAt", l.getLastViewedAt() == null ? null : l.getLastViewedAt().toString());
        }, () -> out.put("active", false));
        return out;
    }

    /** A new link (the old one stops working). The full link is shown once - only its hash is kept. */
    @PostMapping("/api/learn/staff/children/{id}/parent-link")
    public Map<String, Object> create(@PathVariable Long id, @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        access.requireStaff(me);
        access.requireCanSee(me, id);
        profiles.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "אין ילד כזה"));
        links.deleteByProfileId(id);
        byte[] b = new byte[24];
        random.nextBytes(b);
        String code = Base64.getUrlEncoder().withoutPadding().encodeToString(b);
        LocalDateTime now = LocalDateTime.now();
        links.save(new LearnParentLink(id, sha(code), me, now, now.plusDays(DAYS)));
        return Map.of("code", code, "path", "/parent/" + code, "expiresAt", now.plusDays(DAYS).toString());
    }

    @DeleteMapping("/api/learn/staff/children/{id}/parent-link")
    public Map<String, Object> cancel(@PathVariable Long id, @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        access.requireStaff(me);
        access.requireCanSee(me, id);
        links.deleteByProfileId(id);
        return Map.of("active", false);
    }

    /** The parent's page (no log-in). A wrong / old / cancelled code = 404, the same answer for all. */
    @GetMapping("/api/learn/parent-view/{code}")
    public Map<String, Object> view(@PathVariable String code) {
        if (code == null || code.length() < 20 || code.length() > 64) throw notFound();
        LearnParentLink l = links.findByCodeHash(sha(code)).orElseThrow(ParentLinkController::notFound);
        LocalDateTime now = LocalDateTime.now();
        if (l.getExpiresAt().isBefore(now)) throw notFound();
        LearnerProfile p = profiles.findById(l.getProfileId()).filter(x -> !x.isDisabled()).orElseThrow(ParentLinkController::notFound);
        if (l.getLastViewedAt() == null || l.getLastViewedAt().plusMinutes(10).isBefore(now)) {
            l.setLastViewedAt(now);
            links.save(l);
        }
        Map<String, Object> out = new LinkedHashMap<>(reports.parentView(p));
        out.put("expiresAt", l.getExpiresAt().toLocalDate().toString());
        return out;
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "הקישור לא פעיל - אפשר לבקש קישור חדש מהמורה");
    }

    static String sha(String code) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(code.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
