package com.example.brainblocks.learning.controller;

import com.example.brainblocks.config.LearnerAuthConfig;
import com.example.brainblocks.learning.model.LearnClass;
import com.example.brainblocks.learning.model.LearnerProfile;
import com.example.brainblocks.learning.repository.LearnClassRepository;
import com.example.brainblocks.learning.repository.LearnerProfileRepository;
import com.example.brainblocks.learning.service.AccessService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 2026-11-13 (Shir chose: "a child who already has an account types the teacher's code and waits for
 * her"): the child keeps playing as before; the request waits for the class's teacher (or the admin),
 * who accepts - up to the class's limit - or declines.
 */
@RestController
public class ClassJoinController {

    private final LearnerProfileRepository profiles;
    private final LearnClassRepository classes;
    private final AccessService access;
    private final com.example.brainblocks.learning.service.SeatsService seats;

    public ClassJoinController(LearnerProfileRepository profiles, LearnClassRepository classes, AccessService access,
                               com.example.brainblocks.learning.service.SeatsService seats) {
        this.profiles = profiles;
        this.classes = classes;
        this.access = access;
        this.seats = seats;
    }

    private static ResponseStatusException bad(HttpStatus s, String why) {
        return new ResponseStatusException(s, why);
    }

    private LearnerProfile child(Long id, Long me) {
        if (!id.equals(me)) throw bad(HttpStatus.NOT_FOUND, "Profile not found");
        LearnerProfile p = profiles.findById(id).orElseThrow(() -> bad(HttpStatus.NOT_FOUND, "Profile not found"));
        if (p.isTeacher() || p.isAdmin()) throw bad(HttpStatus.BAD_REQUEST, "רק ילדים מצטרפים לכיתה");
        return p;
    }

    private Map<String, Object> status(LearnerProfile p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("classId", p.getClassId());
        m.put("className", p.getClassId() == null ? null : classes.findById(p.getClassId()).map(LearnClass::getName).orElse(null));
        LearnClass pending = p.getPendingClassId() == null ? null : classes.findById(p.getPendingClassId()).orElse(null);
        m.put("pendingClassName", pending == null ? null : pending.getName());
        m.put("pendingTeacher", pending == null ? null
                : profiles.findById(pending.getTeacherId()).map(LearnerProfile::getName).orElse(null));
        return m;
    }

    @GetMapping("/api/learn/profiles/{id}/join-class")
    public Map<String, Object> mine(@PathVariable Long id, @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        return status(child(id, me));
    }

    /** body: { code } - the class code the teacher gave. */
    @PostMapping("/api/learn/profiles/{id}/join-class")
    public Map<String, Object> ask(@PathVariable Long id, @RequestBody Map<String, Object> body,
                                   @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        LearnerProfile p = child(id, me);
        String code = String.valueOf(body.get("code")).trim().toUpperCase();
        LearnClass c = classes.findByJoinCode(code).filter(x -> !x.isClosed())
                .orElseThrow(() -> bad(HttpStatus.NOT_FOUND, "אין כיתה עם הקוד הזה. בדקו אותו עם המורה"));
        if (c.getId().equals(p.getClassId())) throw bad(HttpStatus.BAD_REQUEST, "את/ה כבר בכיתה הזאת");
        seats.requireSeat(c, p.getId(), true); // full: blocked at once
        p.setPendingClassId(c.getId());
        profiles.save(p);
        return status(p);
    }

    @PostMapping("/api/learn/profiles/{id}/join-class/cancel")
    public Map<String, Object> cancel(@PathVariable Long id, @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        LearnerProfile p = child(id, me);
        p.setPendingClassId(null);
        profiles.save(p);
        return status(p);
    }

    // ---- the teacher / the admin ----

    private Set<Long> mineClasses(LearnerProfile v) {
        return access.role(v) == AccessService.Role.ADMIN ? null : access.classIdsOf(v);
    }

    @GetMapping("/api/learn/staff/class-requests")
    public List<Map<String, Object>> requests(@RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        LearnerProfile v = access.requireStaff(me);
        Set<Long> mine = mineClasses(v);
        List<Map<String, Object>> out = new ArrayList<>();
        for (LearnerProfile p : profiles.findAll()) {
            Long to = p.getPendingClassId();
            if (to == null || p.isTeacher() || p.isAdmin() || p.isDisabled() || (mine != null && !mine.contains(to))) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("name", p.getName());
            m.put("avatar", p.getAvatar());
            m.put("grade", p.getGrade());
            m.put("toClassId", to);
            m.put("toClassName", classes.findById(to).map(LearnClass::getName).orElse(""));
            m.put("fromClassName", p.getClassId() == null ? null : classes.findById(p.getClassId()).map(LearnClass::getName).orElse(null));
            out.add(m);
        }
        return out;
    }

    private LearnerProfile request(Long me, Long childId) {
        LearnerProfile v = access.requireStaff(me);
        LearnerProfile p = profiles.findById(childId).filter(x -> x.getPendingClassId() != null)
                .orElseThrow(() -> bad(HttpStatus.NOT_FOUND, "אין בקשה כזו"));
        Set<Long> mine = mineClasses(v);
        if (mine != null && !mine.contains(p.getPendingClassId())) throw bad(HttpStatus.FORBIDDEN, "זו לא כיתה שלך");
        return p;
    }

    @PostMapping("/api/learn/staff/class-requests/{childId}/accept")
    public Map<String, Object> accept(@PathVariable Long childId, @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        LearnerProfile p = request(me, childId);
        LearnClass c = classes.findById(p.getPendingClassId()).orElseThrow(() -> bad(HttpStatus.NOT_FOUND, "אין כיתה כזו"));
        seats.requireSeat(c, p.getId(), false);
        p.setClassId(c.getId());
        p.setPendingClassId(null);
        // as when joining with the code: a class that practices one grade gives it, locked
        if (c.getGrade() != null) {
            p.setGrade(c.getGrade());
            p.setGradeLocked(true);
        }
        profiles.save(p);
        return Map.of("ok", true, "className", c.getName());
    }

    @PostMapping("/api/learn/staff/class-requests/{childId}/decline")
    public Map<String, Object> decline(@PathVariable Long childId, @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        LearnerProfile p = request(me, childId);
        p.setPendingClassId(null);
        profiles.save(p);
        return Map.of("ok", true);
    }
}
