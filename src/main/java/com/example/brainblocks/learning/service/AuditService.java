package com.example.brainblocks.learning.service;

import com.example.brainblocks.learning.model.LearnAudit;
import com.example.brainblocks.learning.model.LearnerProfile;
import com.example.brainblocks.learning.repository.LearnAuditRepository;
import com.example.brainblocks.learning.repository.LearnerProfileRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 2026-11-13 (Shir: security - "a log of who did what"): the staff's changes, newest first. */
@Service
public class AuditService {

    private final LearnAuditRepository audits;
    private final LearnerProfileRepository profiles;

    public AuditService(LearnAuditRepository audits, LearnerProfileRepository profiles) {
        this.audits = audits;
        this.profiles = profiles;
    }

    public void record(Long actorId, String method, String path) {
        try {
            audits.save(new LearnAudit(actorId, method, path));
        } catch (RuntimeException ignored) {
            // the log must never break the action itself
        }
    }

    /** The last 300 changes, with the actor's name and the name of a child the path points at. */
    public List<Map<String, Object>> recent() {
        Map<Long, LearnerProfile> byId = profiles.findAll().stream().collect(Collectors.toMap(LearnerProfile::getId, Function.identity()));
        List<Map<String, Object>> out = new ArrayList<>();
        for (LearnAudit a : audits.findTop300ByOrderByIdDesc()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("at", a.getAt());
            LearnerProfile who = byId.get(a.getActorId());
            m.put("actor", who == null ? "?" : who.getName());
            m.put("actorRole", who == null ? "" : who.isAdmin() ? "מנהלת" : who.isTeacher() ? "מורה" : "");
            m.put("method", a.getMethod());
            m.put("path", a.getPath());
            java.util.regex.Matcher c = java.util.regex.Pattern.compile("/(children|teachers|class-requests)/(\\d+)").matcher(a.getPath());
            if (c.find()) {
                LearnerProfile target = byId.get(Long.valueOf(c.group(2)));
                m.put("target", target == null ? "(נמחק)" : target.getName());
            }
            out.add(m);
        }
        return out;
    }
}
