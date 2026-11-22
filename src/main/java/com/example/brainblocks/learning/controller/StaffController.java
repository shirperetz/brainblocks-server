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
            m.put("maxStudents", c.getMaxStudents());
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
        // 2026-11-13 (Shir: "I, the admin, set how many a class can have; she approves up to that"): only the admin.
        if (body.containsKey("maxStudents")) {
            if (access.role(v) != AccessService.Role.ADMIN) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "רק המנהלת קובעת כמה תלמידים בכיתה");
            }
            Object raw = body.get("maxStudents");
            Integer max = raw == null || raw.toString().isBlank() || "0".equals(raw.toString()) ? null : Integer.valueOf(raw.toString().trim());
            if (max != null && (max < 1 || max > 200)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "מספר תלמידים: 1 עד 200");
            long now = approvedIn(c.getId(), null);
            if (max != null && now > max) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "יש כבר " + now + " תלמידים בכיתה - אי אפשר פחות מזה");
            }
            c.setMaxStudents(max);
        }
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
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", c.getId());
        out.put("name", c.getName());
        out.put("joinCode", c.getJoinCode());
        out.put("closed", c.isClosed());
        out.put("maxStudents", c.getMaxStudents());
        return out;
    }

    /** How many approved children are in the class (not counting one child, e.g. the one being moved). */
    private long approvedIn(Long classId, Long except) {
        return profiles.findAll().stream().filter(p -> classId.equals(p.getClassId()) && p.isApproved()
                && !p.isTeacher() && !p.isAdmin() && !p.getId().equals(except)).count();
    }


    /**
     * 2026-11-12 (Shir: "possible to delete if needed, and it won't be seen"): the class disappears - its
     * code stops working, its weekly tasks go with it. The children are NOT deleted: they stay, with
     * "no class" (and a grade the class had locked is unlocked). Only the class's teacher or the admin.
     */
    @org.springframework.web.bind.annotation.DeleteMapping("/classes/{id}")
    @org.springframework.transaction.annotation.Transactional
    public Map<String, Object> deleteClass(@PathVariable Long id, @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        LearnerProfile v = access.requireStaff(me);
        LearnClass c = ownClass(v, id);
        int moved = 0;
        for (LearnerProfile p : profiles.findAll()) {
            if (c.getId().equals(p.getClassId())) {
                p.setClassId(null);
                if (c.getGrade() != null && !p.isTeacher() && !p.isAdmin()) p.setGradeLocked(false);
                profiles.save(p);
                moved++;
            }
        }
        if (assignmentRepository != null) {
            assignmentRepository.deleteAll(assignmentRepository.findByClassIdOrderByStartDateDescIdDesc(c.getId()));
        }
        classes.delete(c);
        return Map.of("deleted", true, "childrenMoved", moved);
    }

    private com.example.brainblocks.learning.repository.LearnAssignmentRepository assignmentRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setAssignmentRepository(com.example.brainblocks.learning.repository.LearnAssignmentRepository r) {
        this.assignmentRepository = r;
    }

    private LearnClass ownClass(LearnerProfile v, Long id) {
        LearnClass c = classes.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "אין כיתה כזו"));
        if (access.role(v) != AccessService.Role.ADMIN && !c.getTeacherId().equals(v.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "זו לא כיתה שלך");
        }
        return c;
    }

    private static String className(Object raw) {
        String n = com.example.brainblocks.learning.service.Names.clean(raw, 31, "שם הכיתה");
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

    // 2026-11-13 (Shir: "practice from the report"): a short task for this child only
    @GetMapping("/children/{id}/practice")
    public List<Map<String, Object>> practice(@PathVariable Long id, @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        return assignments.personalOf(me, id);
    }

    /** body: { topic, rounds? (2), days? (3) } */
    @PostMapping("/children/{id}/practice")
    public Map<String, Object> givePractice(@PathVariable Long id, @RequestBody Map<String, Object> body,
                                            @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        return assignments.createForChild(me, id, body.get("topic"), body.get("rounds"), body.get("days"));
    }

    /**
     * 2026-11-21 (Shir: "harder when very good, but a higher grade only with the teacher's approval"):
     * body { topic: "FRACTIONS", grade: "G6" } opens that topic at a higher grade for this child;
     * grade null/"" = back to the child's own grade. The child's teacher or the admin (staffOver).
     */
    @PostMapping("/children/{id}/challenge")
    public Map<String, Object> challenge(@PathVariable Long id, @RequestBody Map<String, Object> body,
                                         @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        staffOver(me, id);
        LearnerProfile p = profiles.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "אין ילד כזה"));
        com.example.brainblocks.learning.model.Topic t;
        try {
            t = com.example.brainblocks.learning.model.Topic.valueOf(String.valueOf(body.get("topic")).trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "נושא לא מוכר");
        }
        if (t.isGame() || t == com.example.brainblocks.learning.model.Topic.MIXED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "אפשר לפתוח רק נושא תרגול רגיל");
        }
        Object raw = body.get("grade");
        com.example.brainblocks.learning.model.Grade g = raw == null || raw.toString().isBlank() ? null : grade(raw);
        if (g != null) {
            if (p.getGrade() != null && g.ordinal() <= p.getGrade().ordinal()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "צריך לבחור כיתה גבוהה מהכיתה של הילד");
            }
            if (!com.example.brainblocks.learning.service.GradeTopics.allowed(t, g)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "הנושא הזה לא נלמד בכיתה הזאת");
            }
        }
        p.setAdvancedLevel(t, g);
        profiles.save(p);
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        Map<String, String> adv = new java.util.LinkedHashMap<>();
        p.getAdvancedLevels().forEach((k, v) -> adv.put(k.name(), v.name()));
        out.put("advanced", adv);
        return out;
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
        // 2026-11-12 (Shir: a teacher gets "a new password for a child" - not more): a teacher changes only
        // the password of her own child; a new username is the admin's.
        boolean admin = access.role(access.requireStaff(me)) == AccessService.Role.ADMIN;
        return accounts.setCredentials(id, admin ? body.get("username") : null, body.get("password"));
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
        // 2026-11-12 (Shir): may the child change its own grade - the admin or the child's teacher
        // (staffOver above already checked this child is hers).
        if (body.get("changeAllowed") != null) {
            p.setGradeChangeAllowed(Boolean.parseBoolean(body.get("changeAllowed").toString()));
        }
        profiles.save(p);
        return ProfileResponse.fromEntity(p);
    }

    /** 2026-11-21 (calm mode): body { calm: true|false } - the child's teacher or the admin. */
    @PostMapping("/children/{id}/calm")
    public ProfileResponse setCalm(@PathVariable Long id, @RequestBody Map<String, Object> body,
                                   @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        staffOver(me, id);
        LearnerProfile p = profiles.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "אין ילד כזה"));
        p.setCalm(Boolean.parseBoolean(String.valueOf(body.get("calm"))));
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

    /**
     * Move a child to a class (classId null/0 = Shir's own, no class). 2026-11-12 (Shir: "the option to
     * switch classes - for me as admin and for the teachers"): the admin moves any child anywhere; a
     * teacher moves only a child of HER classes into another of HER classes (never out of every class).
     */
    @PostMapping("/children/{id}/class")
    public ProfileResponse moveChild(@PathVariable Long id, @RequestBody Map<String, Object> body,
                                     @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        LearnerProfile v = access.requireStaff(me);
        boolean admin = access.role(v) == AccessService.Role.ADMIN;
        LearnerProfile p = profiles.findById(id).filter(x -> !x.isTeacher() && !x.isAdmin())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "אין ילד כזה"));
        if (!admin) access.requireCanSee(me, id);
        Object raw = body.get("classId");
        Long classId = raw == null || "0".equals(raw.toString()) ? null : Long.valueOf(raw.toString());
        if (classId == null && !admin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "רק המנהלת מוציאה ילד מכל הכיתות");
        }
        LearnClass target = classId == null ? null : classes.findById(classId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "אין כיתה כזו"));
        if (target != null && !admin && !target.getTeacherId().equals(v.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "זו לא כיתה שלך");
        }
        if (p.isApproved() && !java.util.Objects.equals(p.getClassId(), classId)) seats.requireSeat(target, id, false);
        p.setClassId(classId);
        // A class that practices one grade gives it to the child, locked (as when joining with its code).
        if (target != null && target.getGrade() != null) {
            p.setGrade(target.getGrade());
            p.setGradeLocked(true);
        }
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
            m.put("maxStudents", t.getMaxStudents());
            m.put("students", seats.ofTeacher(t.getId()));
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

    /** 2026-11-13 (Shir: "I decide how many students a teacher has"): body { maxStudents } - 0/empty = no limit. */
    @PostMapping("/teachers/{id}/max-students")
    public Map<String, Object> teacherMax(@PathVariable Long id, @RequestBody Map<String, Object> body,
                                          @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        access.requireAdmin(me);
        requireTeacher(id);
        LearnerProfile t = profiles.findById(id).orElseThrow();
        Object raw = body.get("maxStudents");
        Integer max = raw == null || raw.toString().isBlank() || "0".equals(raw.toString().trim()) ? null : Integer.valueOf(raw.toString().trim());
        if (max != null && (max < 1 || max > 1000)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "מספר תלמידים: 1 עד 1000");
        long now = seats.ofTeacher(id);
        if (max != null && now > max) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "יש לה כבר " + now + " תלמידים - אי אפשר פחות מזה");
        }
        t.setMaxStudents(max);
        profiles.save(t);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id);
        out.put("maxStudents", max);
        out.put("students", now);
        return out;
    }

    private com.example.brainblocks.learning.service.SeatsService seats;

    @org.springframework.beans.factory.annotation.Autowired
    void setSeats(com.example.brainblocks.learning.service.SeatsService seats) {
        this.seats = seats;
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

    // ---- the class building (idea #6, 2026-11-12) ----

    private com.example.brainblocks.learning.service.ClassBuildingService building;

    @org.springframework.beans.factory.annotation.Autowired
    void setBuilding(com.example.brainblocks.learning.service.ClassBuildingService building) {
        this.building = building;
    }

    @GetMapping("/classes/{id}/building")
    public Map<String, Object> classBuilding(@PathVariable Long id, @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        return building.forClass(me, id);
    }

    // 2026-11-21 (Shir chose): "what to teach tomorrow" + Meitzav readiness, per class
    @org.springframework.beans.factory.annotation.Autowired
    private com.example.brainblocks.learning.service.TeacherInsightsService insights;

    @GetMapping("/classes/{id}/tomorrow")
    public Map<String, Object> tomorrow(@PathVariable Long id, @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        return insights.tomorrow(me, id);
    }

    /** 2026-11-22: the class's week - who practised, who improved, what is still hard. */
    @GetMapping("/classes/{id}/weekly")
    public Map<String, Object> weekly(@PathVariable Long id, @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        return insights.weekly(me, id);
    }

    @GetMapping("/classes/{id}/meitzav")
    public Map<String, Object> meitzav(@PathVariable Long id, @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        return insights.meitzav(me, id);
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
