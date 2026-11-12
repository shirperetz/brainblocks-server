package com.example.brainblocks.learning.controller;

import com.example.brainblocks.config.LearnerAuthConfig;
import com.example.brainblocks.learning.dto.response.ProfileResponse;
import com.example.brainblocks.learning.model.LearnClass;
import com.example.brainblocks.learning.model.LearnerProfile;
import com.example.brainblocks.learning.model.LearnerStageProgress;
import com.example.brainblocks.learning.repository.LearnClassRepository;
import com.example.brainblocks.learning.repository.LearnerProfileRepository;
import com.example.brainblocks.learning.repository.LearnerStageProgressRepository;
import com.example.brainblocks.learning.service.AccountService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 2026-11-12 (Shir: "in the admin screen I can make my users teachers, and under each teacher the
 * children of her class - I click the teacher and the students appear"): the whole school as a tree -
 * teachers (and Shir) -> their classes -> the children; children in no teacher's class in their own
 * group so nobody falls between the chairs. Plus making a user a teacher / a student again. Admin only.
 */
@RestController
@RequestMapping("/api/learn/admin")
public class AdminTreeController {

    private final AccountService accounts;
    private final LearnerProfileRepository profiles;
    private final LearnClassRepository classes;
    private final LearnerStageProgressRepository progress;

    public AdminTreeController(AccountService accounts, LearnerProfileRepository profiles,
                               LearnClassRepository classes, LearnerStageProgressRepository progress) {
        this.accounts = accounts;
        this.profiles = profiles;
        this.classes = classes;
        this.progress = progress;
    }

    private void requireAdmin(Long me) {
        if (!accounts.isAdmin(me)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "רק למנהלת");
    }

    @GetMapping("/tree")
    public Map<String, Object> tree(@RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        requireAdmin(me);
        List<LearnerProfile> all = profiles.findAll();
        List<LearnClass> allClasses = classes.findAll();
        Map<Long, LearnerProfile> byId = all.stream().collect(Collectors.toMap(LearnerProfile::getId, p -> p));

        // Staff = teachers + admins (Shir's own classes appear under her card).
        List<LearnerProfile> staff = all.stream()
                .filter(p -> p.isTeacher() || p.isAdmin())
                .sorted(Comparator.comparing((LearnerProfile p) -> !p.isAdmin()).thenComparing(LearnerProfile::getName,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        Set<Long> staffIds = staff.stream().map(LearnerProfile::getId).collect(Collectors.toSet());
        Set<Long> classesWithTeacher = allClasses.stream()
                .filter(c -> c.getTeacherId() != null && staffIds.contains(c.getTeacherId()))
                .map(LearnClass::getId).collect(Collectors.toSet());

        List<Map<String, Object>> teachers = new ArrayList<>();
        for (LearnerProfile t : staff) {
            Map<String, Object> tm = person(t);
            List<Map<String, Object>> cls = new ArrayList<>();
            for (LearnClass c : allClasses.stream().filter(c -> t.getId().equals(c.getTeacherId()))
                    .sorted(Comparator.comparing(LearnClass::getName, Comparator.nullsLast(Comparator.naturalOrder()))).toList()) {
                Map<String, Object> cm = new LinkedHashMap<>();
                cm.put("id", c.getId());
                cm.put("name", c.getName());
                cm.put("grade", c.getGrade());
                cm.put("closed", c.isClosed());
                cm.put("children", children(all.stream()
                        .filter(p -> !p.isTeacher() && !p.isAdmin() && c.getId().equals(p.getClassId())).toList()));
                cls.add(cm);
            }
            tm.put("classes", cls);
            tm.put("childCount", cls.stream().mapToInt(c -> ((List<?>) c.get("children")).size()).sum());
            teachers.add(tm);
        }
        List<LearnerProfile> orphans = all.stream()
                .filter(p -> !p.isTeacher() && !p.isAdmin())
                .filter(p -> p.getClassId() == null || !classesWithTeacher.contains(p.getClassId()))
                .toList();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("teachers", teachers);
        out.put("noTeacher", children(orphans));
        return out;
    }

    private Map<String, Object> person(LearnerProfile p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId());
        m.put("name", p.getName());
        m.put("username", p.getUsername());
        m.put("admin", p.isAdmin());
        m.put("teacher", p.isTeacher());
        m.put("approved", p.isApproved());
        m.put("disabled", p.isDisabled());
        return m;
    }

    private List<Map<String, Object>> children(List<LearnerProfile> kids) {
        return kids.stream()
                .sorted(Comparator.comparing(LearnerProfile::getLastActiveAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(p -> {
                    Map<String, Object> m = person(p);
                    m.put("grade", p.getGrade());
                    m.put("avatar", p.getAvatar());
                    m.put("lastActiveAt", p.getLastActiveAt());
                    m.put("stars", progress.findByProfileId(p.getId()).stream().mapToInt(LearnerStageProgress::getStarsEarned).sum());
                    m.put("classId", p.getClassId());
                    return m;
                })
                .toList();
    }

    /**
     * body { teacher: true|false }. A student becomes a teacher (leaves its class, so it isn't listed as a
     * child); a teacher goes back to being a student only when she has no classes (move them first) -
     * nothing is deleted. The admin herself is never changed here.
     */
    @PostMapping("/children/{id}/teacher")
    public ProfileResponse setTeacher(@PathVariable Long id, @RequestBody Map<String, Boolean> body,
                                      @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        requireAdmin(me);
        LearnerProfile p = profiles.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "אין משתמש כזה"));
        if (p.isAdmin()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "המנהלת נשארת מנהלת");
        boolean teacher = Boolean.TRUE.equals(body.get("teacher"));
        if (teacher == p.isTeacher()) return ProfileResponse.fromEntity(p);
        if (!teacher && !classes.findByTeacherId(p.getId()).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "למורה יש כיתות - קודם להעביר אותן למורה אחרת");
        }
        p.setTeacher(teacher);
        if (teacher) {
            p.setClassId(null);
            p.setGradeLocked(false);
        }
        return ProfileResponse.fromEntity(profiles.save(p));
    }

    /** body { teacherId } - give a class to another teacher (or to the admin herself). Its children move with it. */
    @PostMapping("/classes/{id}/teacher")
    public Map<String, Object> moveClass(@PathVariable Long id, @RequestBody Map<String, Long> body,
                                         @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        requireAdmin(me);
        LearnClass c = classes.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "אין כיתה כזאת"));
        Long to = body.get("teacherId");
        LearnerProfile t = to == null ? null : profiles.findById(to).orElse(null);
        if (t == null || !(t.isTeacher() || t.isAdmin())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "צריך לבחור מורה");
        }
        c.setTeacherId(t.getId());
        classes.save(c);
        return Map.of("id", c.getId(), "teacherId", t.getId());
    }
}
