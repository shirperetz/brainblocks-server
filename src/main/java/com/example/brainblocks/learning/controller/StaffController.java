package com.example.brainblocks.learning.controller;

import com.example.brainblocks.config.LearnerAuthConfig;
import com.example.brainblocks.learning.dto.response.ProfileResponse;
import com.example.brainblocks.learning.model.LearnClass;
import com.example.brainblocks.learning.model.LearnerProfile;
import com.example.brainblocks.learning.repository.LearnClassRepository;
import com.example.brainblocks.learning.repository.LearnerProfileRepository;
import com.example.brainblocks.learning.service.AccessService;
import com.example.brainblocks.learning.service.AccountService;
import com.example.brainblocks.learning.service.ReportService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 2026-11-11 (Shir: "teachers see their students, I see mine, I oversee everything"): the screens of
 * teachers and of the admin. Every call goes through AccessService - a teacher can only touch the
 * children of her own classes; the admin everything. Reversible only: block/unblock, close a class,
 * new code, new password, move a child. Nothing is deleted.
 */
@RestController
@RequestMapping("/api/learn/staff")
public class StaffController {

    private final AccessService access;
    private final AccountService accounts;
    private final ReportService reports;
    private final LearnClassRepository classes;
    private final LearnerProfileRepository profiles;

    public StaffController(AccessService access, AccountService accounts, ReportService reports,
                           LearnClassRepository classes, LearnerProfileRepository profiles) {
        this.access = access;
        this.accounts = accounts;
        this.reports = reports;
        this.classes = classes;
        this.profiles = profiles;
    }

    // ---- who am I, my classes ----

    @GetMapping("/me")
    public Map<String, Object> me(@RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        LearnerProfile v = access.requireStaff(me);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", v.getId());
        m.put("name", v.getName());
        m.put("role", access.role(v).name());
        m.put("classes", classList(v));
        return m;
    }

    private List<Map<String, Object>> classList(LearnerProfile v) {
        boolean admin = access.role(v) == AccessService.Role.ADMIN;
        List<LearnClass> list = admin ? classes.findAll() : classes.findByTeacherId(v.getId());
        List<LearnerProfile> everyone = profiles.findAll();
        List<Map<String, Object>> out = new ArrayList<>();
        for (LearnClass c : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getId());
            m.put("name", c.getName());
            m.put("joinCode", c.getJoinCode());
            m.put("closed", c.isClosed());
            m.put("grade", c.getGrade());
            m.put("teacherId", c.getTeacherId());
            m.put("teacherName", everyone.stream().filter(p -> p.getId().equals(c.getTeacherId()))
                    .map(LearnerProfile::getName).findFirst().orElse(""));
            m.put("children", everyone.stream().filter(p -> c.getId().equals(p.getClassId()) && p.isApproved()).count());
            m.put("waiting", everyone.stream().filter(p -> c.getId().equals(p.getClassId()) && !p.isApproved() && !p.isDisabled()).count());
            out.add(m);
        }
        return out;
    }

    /** body: { name, teacherId? (admin only) } */
    @PostMapping("/classes")
    public Map<String, Object> createClass(@RequestBody Map<String, Object> body,
                                           @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        LearnerProfile v = access.requireStaff(me);
        String name = className(body.get("name"));
        Long teacherId = v.getId();
        if (access.role(v) == AccessService.Role.ADMIN && body.get("teacherId") != null) {
            teacherId = Long.valueOf(body.get("teacherId").toString());
            LearnerProfile t = profiles.findById(teacherId).filter(p -> p.isTeacher() || p.isAdmin())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "אין מורה כזו"));
            teacherId = t.getId();
        }
        LearnClass created = new LearnClass(name, teacherId, accounts.newClassCode());
        if (body.get("grade") != null && !body.get("grade").toString().isBlank()) created.setGrade(grade(body.get("grade")));
        LearnClass c = classes.save(created);
        return Map.of("id", c.getId(), "name", c.getName(), "joinCode", c.getJoinCode());
    }

    /** body: { name?, closed?, newCode? } */
    @PatchMapping("/classes/{id}")
    public Map<String, Object> updateClass(@PathVariable Long id, @RequestBody Map<String, Object> body,
                                           @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        LearnerProfile v = access.requireStaff(me);
        LearnClass c = ownClass(v, id);
        if (body.get("name") != null) c.setName(className(body.get("name")));
        if (body.get("closed") != null) c.setClosed(Boolean.parseBoolean(body.get("closed").toString()));
        if (Boolean.TRUE.equals(body.get("newCode"))) c.setJoinCode(accounts.newClassCode());
        // 2026-11-12: the class's grade - every child in it gets it, locked ("" = no grade, unlocks them).
        if (body.containsKey("grade")) {
            Object g = body.get("grade");
            var newGrade = g == null || g.toString().isBlank() ? null : grade(g);
            c.setGrade(newGrade);
            for (LearnerProfile p : profiles.findAll()) {
                if (c.getId().equals(p.getClassId()) && !p.isTeacher() && !p.isAdmin()) {
                    if (newGrade != null) p.setGrade(newGrade);
                    p.setGradeLocked(newGrade != null);
                    profiles.save(p);
                }
            }
        }
        classes.save(c);
        return Map.of("id", c.getId(), "name", c.getName(), "joinCode", c.getJoinCode(), "closed", c.isClosed());
    }

    private LearnClass ownClass(LearnerProfile v, Long id) {
        LearnClass c = classes.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "אין כיתה כזו"));
        if (access.role(v) != AccessService.Role.ADMIN && !c.getTeacherId().equals(v.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "זו לא כיתה שלך");
        }
        return c;
    }

    private static String className(Object raw) {
        String n = raw == null ? "" : raw.toString().trim();
        if (n.isEmpty() || n.length() > 30) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "שם כיתה: 1 עד 30 תווים");
        return n;
    }

    // ---- children ----

    /** classId: a class / 0 = Shir's own children without a class / absent = everyone I see. */
    @GetMapping("/children")
    public List<Map<String, Object>> children(@RequestParam(required = false) Long classId,
                                              @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (LearnerProfile p : access.visibleChildren(me, classId)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("name", p.getName());
            m.put("username", p.getUsername());
            m.put("avatar", p.getAvatar());
            m.put("grade", p.getGrade());
            m.put("classId", p.getClassId());
            m.put("gradeLocked", p.isGradeLocked());
            m.put("approved", p.isApproved());
            m.put("disabled", p.isDisabled());
            m.put("lastActiveAt", p.getLastActiveAt());
            m.put("createdAt", p.getCreatedAt());
            out.add(m);
        }
        return out;
    }

    @GetMapping("/overview")
    public Map<String, Object> overview(@RequestParam(required = false) Long classId,
                                        @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        return reports.overview(me, classId);
    }

    @GetMapping("/children/{id}/report")
    public Map<String, Object> report(@PathVariable Long id, @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        access.requireStaff(me);
        return reports.childReport(me, id);
    }

    @PostMapping("/children/{id}/approve")
    public ProfileResponse approve(@PathVariable Long id, @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        staffOver(me, id);
        return accounts.approve(id);
    }

    @PostMapping("/children/{id}/disabled")
    public ProfileResponse disabled(@PathVariable Long id, @RequestBody Map<String, Boolean> body,
                                    @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        staffOver(me, id);
        return accounts.setDisabled(id, Boolean.TRUE.equals(body.get("disabled")), me);
    }

    @PostMapping("/children/{id}/password")
    public ProfileResponse password(@PathVariable Long id, @RequestBody Map<String, String> body,
                                    @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        staffOver(me, id);
        return accounts.setCredentials(id, body.get("username"), body.get("password"));
    }

    /**
     * 2026-11-12 (Shir: "limit the child to a specific grade"): body { grade: "G4", locked: true }.
     * The child's teacher or the admin. locked=false lets the child choose again.
     */
    @PostMapping("/children/{id}/grade")
    public ProfileResponse setGrade(@PathVariable Long id, @RequestBody Map<String, Object> body,
                                    @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        staffOver(me, id);
        LearnerProfile p = profiles.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "אין ילד כזה"));
        if (body.get("grade") != null) p.setGrade(grade(body.get("grade")));
        if (body.get("locked") != null) p.setGradeLocked(Boolean.parseBoolean(body.get("locked").toString()));
        profiles.save(p);
        return ProfileResponse.fromEntity(p);
    }

    private static com.example.brainblocks.learning.model.Grade grade(Object raw) {
        try {
            return com.example.brainblocks.learning.model.Grade.valueOf(raw.toString().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "כיתה לא מוכרת");
        }
    }

    /** Admin only: move a child to a class (classId null/0 = Shir's own). */
    @PostMapping("/children/{id}/class")
    public ProfileResponse moveChild(@PathVariable Long id, @RequestBody Map<String, Object> body,
                                     @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        access.requireAdmin(me);
        LearnerProfile p = profiles.findById(id).filter(x -> !x.isTeacher() && !x.isAdmin())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "אין ילד כזה"));
        Object raw = body.get("classId");
        Long classId = raw == null || "0".equals(raw.toString()) ? null : Long.valueOf(raw.toString());
        if (classId != null && classes.findById(classId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "אין כיתה כזו");
        }
        p.setClassId(classId);
        profiles.save(p);
        return ProfileResponse.fromEntity(p);
    }

    /** The viewer is staff and the child is one of hers (never a teacher/admin through here, unless admin). */
    private void staffOver(Long me, Long childId) {
        access.requireStaff(me);
        access.requireCanSee(me, childId);
    }

    // ---- teachers (admin only) ----

    @GetMapping("/teachers")
    public List<Map<String, Object>> teachers(@RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        access.requireAdmin(me);
        List<LearnClass> all = classes.findAll();
        List<Map<String, Object>> out = new ArrayList<>();
        for (LearnerProfile t : profiles.findAll()) {
            if (!t.isTeacher()) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId());
            m.put("name", t.getName());
            m.put("username", t.getUsername());
            m.put("approved", t.isApproved());
            m.put("disabled", t.isDisabled());
            m.put("createdAt", t.getCreatedAt());
            m.put("lastActiveAt", t.getLastActiveAt());
            m.put("classes", all.stream().filter(c -> c.getTeacherId().equals(t.getId())).map(LearnClass::getName).toList());
            out.add(m);
        }
        return out;
    }

    @PostMapping("/teachers/{id}/approve")
    public ProfileResponse approveTeacher(@PathVariable Long id, @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        access.requireAdmin(me);
        requireTeacher(id);
        return accounts.approve(id);
    }

    @PostMapping("/teachers/{id}/disabled")
    public ProfileResponse disableTeacher(@PathVariable Long id, @RequestBody Map<String, Boolean> body,
                                          @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        access.requireAdmin(me);
        requireTeacher(id);
        return accounts.setDisabled(id, Boolean.TRUE.equals(body.get("disabled")), me);
    }

    private void requireTeacher(Long id) {
        profiles.findById(id).filter(LearnerProfile::isTeacher)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "אין מורה כזו"));
    }

    @GetMapping("/teacher-code")
    public Map<String, String> teacherCode(@RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        access.requireAdmin(me);
        return Map.of("code", accounts.teacherCode());
    }

    @PutMapping("/teacher-code")
    public Map<String, String> changeTeacherCode(@RequestBody Map<String, String> body,
                                                 @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        access.requireAdmin(me);
        return Map.of("code", accounts.changeTeacherCode(body.get("code")));
    }

    // ---- weekly tasks (idea #5, 2026-11-12) ----

    private com.example.brainblocks.learning.service.AssignmentService assignments;

    @org.springframework.beans.factory.annotation.Autowired
    void setAssignments(com.example.brainblocks.learning.service.AssignmentService assignments) {
        this.assignments = assignments;
    }

    /** The class's tasks with each child's progress (the class's teacher or the admin). */
    @GetMapping("/classes/{id}/assignments")
    public List<Map<String, Object>> classAssignments(@PathVariable Long id,
                                                      @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        return assignments.forClass(me, id);
    }

    /** body: { topic: "FRACTIONS", rounds: 3, days: 7 } */
    @PostMapping("/classes/{id}/assignments")
    public Map<String, Object> createAssignment(@PathVariable Long id, @RequestBody Map<String, Object> body,
                                                @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        var a = assignments.create(me, id, body.get("topic"), body.get("rounds"), body.get("days"));
        return Map.of("id", a.getId(), "topic", a.getTopic(), "rounds", a.getRounds(), "endDate", a.getEndDate().toString());
    }

    @PostMapping("/assignments/{id}/cancel")
    public Map<String, Object> cancelAssignment(@PathVariable Long id, @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        assignments.cancel(me, id);
        return Map.of("cancelled", true);
    }

    /** Which of my classes a list of ids belongs to - unused ids are simply not mine. */
    Set<Long> myClassIds(Long me) {
        return access.classIdsOf(access.requireStaff(me));
    }
}
