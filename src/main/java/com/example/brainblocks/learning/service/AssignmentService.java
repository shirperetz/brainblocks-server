package com.example.brainblocks.learning.service;

import com.example.brainblocks.learning.model.LearnAssignment;
import com.example.brainblocks.learning.model.LearnClass;
import com.example.brainblocks.learning.model.LearnerProfile;
import com.example.brainblocks.learning.model.LearnerSession;
import com.example.brainblocks.learning.model.SessionMode;
import com.example.brainblocks.learning.model.Topic;
import com.example.brainblocks.learning.repository.LearnAssignmentRepository;
import com.example.brainblocks.learning.repository.LearnClassRepository;
import com.example.brainblocks.learning.repository.LearnerProfileRepository;
import com.example.brainblocks.learning.repository.LearnerSessionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 2026-11-12 (idea #5): the weekly task a teacher gives her class - "3 rounds of fractions this week".
 * The teacher sets it for one of HER classes (same rule as AccessService), the children of that class
 * see it with a progress bar, the teacher sees who finished. Progress is counted from the finished
 * rounds, never stored, so it can't drift.
 */
@Service
public class AssignmentService {

    /** A class holds at most this many running tasks (more is noise for a child). */
    public static final int MAX_ACTIVE = 3;
    public static final int MAX_ROUNDS = 10;
    public static final int MAX_DAYS = 14;
    /** Ended tasks the teacher still sees in the list. */
    private static final int HISTORY_DAYS = 28;

    private final LearnAssignmentRepository assignments;
    private final LearnClassRepository classes;
    private final LearnerProfileRepository profiles;
    private final LearnerSessionRepository sessions;
    private final AccessService access;

    public AssignmentService(LearnAssignmentRepository assignments, LearnClassRepository classes,
                             LearnerProfileRepository profiles, LearnerSessionRepository sessions, AccessService access) {
        this.assignments = assignments;
        this.classes = classes;
        this.profiles = profiles;
        this.sessions = sessions;
        this.access = access;
    }

    /**
     * A round counts toward the task: a STAGE round (not the daily / 60 seconds) of the task's topic,
     * inside its dates, with at least half the answers right - tapping at random doesn't finish a task.
     */
    static boolean counts(LearnerSession s, LearnAssignment a) {
        if (s.getMode() != SessionMode.STAGE || s.getTopic() != a.getTopic() || s.getCreatedAt() == null) return false;
        // Only rounds played after the teacher set it (the bot found: this morning's rounds finished it at once).
        if (a.getCreatedAt() != null && s.getCreatedAt().isBefore(a.getCreatedAt())) return false;
        LocalDate d = s.getCreatedAt().toLocalDate();
        if (d.isBefore(a.getStartDate()) || d.isAfter(a.getEndDate())) return false;
        return s.getTotalQuestions() > 0 && s.getCorrectCount() * 2 >= s.getTotalQuestions();
    }

    static int done(List<LearnerSession> childSessions, LearnAssignment a) {
        int n = (int) childSessions.stream().filter(s -> counts(s, a)).count();
        return Math.min(n, a.getRounds());
    }

    // ---- staff ----

    public LearnAssignment create(Long me, Long classId, Object rawTopic, Object rawRounds, Object rawDays) {
        LearnerProfile v = access.requireStaff(me);
        LearnClass c = ownClass(v, classId);
        Topic topic = topic(rawTopic);
        int rounds = number(rawRounds, 3, 1, MAX_ROUNDS, "מספר סבבים: 1 עד " + MAX_ROUNDS);
        int days = number(rawDays, 7, 1, MAX_DAYS, "משך: 1 עד " + MAX_DAYS + " ימים");
        if (!GradeTopics.allowed(topic, c.getGrade())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "הנושא לא שייך לכיתה שמתרגלים בה");
        }
        LocalDate today = LocalDate.now();
        long running = assignments.findByClassIdOrderByStartDateDescIdDesc(c.getId()).stream()
                .filter(a -> a.isActiveOn(today)).count();
        if (running >= MAX_ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "כבר יש " + MAX_ACTIVE + " משימות פעילות לכיתה - אפשר לבטל אחת");
        }
        return assignments.save(new LearnAssignment(c.getId(), topic, rounds, today, today.plusDays(days - 1L), v.getId()));
    }

    public void cancel(Long me, Long assignmentId) {
        LearnerProfile v = access.requireStaff(me);
        LearnAssignment a = assignments.findById(assignmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "אין משימה כזו"));
        ownClass(v, a.getClassId());
        a.setCancelled(true);
        assignments.save(a);
    }

    /** The class's tasks (running + ended in the last 4 weeks), each with every approved child's progress. */
    public List<Map<String, Object>> forClass(Long me, Long classId) {
        LearnerProfile v = access.requireStaff(me);
        LearnClass c = ownClass(v, classId);
        LocalDate today = LocalDate.now();
        List<LearnerProfile> kids = profiles.findAll().stream()
                .filter(p -> c.getId().equals(p.getClassId()) && !p.isTeacher() && !p.isAdmin()
                        && p.isApproved() && !p.isDisabled())
                .sorted(Comparator.comparing(LearnerProfile::getName, Comparator.nullsLast(String::compareTo)))
                .toList();
        Map<Long, List<LearnerSession>> byKid = new HashMap<>();
        for (LearnerProfile k : kids) byKid.put(k.getId(), sessions.findByProfileId(k.getId()));

        List<Map<String, Object>> out = new ArrayList<>();
        for (LearnAssignment a : assignments.findByClassIdOrderByStartDateDescIdDesc(c.getId())) {
            if (a.isCancelled() || a.getEndDate().isBefore(today.minusDays(HISTORY_DAYS))) continue;
            Map<String, Object> m = describe(a, today);
            List<Map<String, Object>> rows = new ArrayList<>();
            int finished = 0;
            for (LearnerProfile k : kids) {
                int d = done(byKid.get(k.getId()), a);
                if (d >= a.getRounds()) finished++;
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("childId", k.getId());
                r.put("name", k.getName());
                r.put("avatar", k.getAvatar());
                r.put("done", d);
                rows.add(r);
            }
            m.put("children", rows);
            m.put("finished", finished);
            m.put("total", kids.size());
            out.add(m);
        }
        return out;
    }

    // ---- the child ----

    /** The running tasks of the child's class, with its own progress. A child without a class: none. */
    public List<Map<String, Object>> forChild(LearnerProfile child) {
        if (child.getClassId() == null) return List.of();
        LocalDate today = LocalDate.now();
        List<LearnerSession> mine = sessions.findByProfileId(child.getId());
        List<Map<String, Object>> out = new ArrayList<>();
        for (LearnAssignment a : assignments.findByClassIdOrderByStartDateDescIdDesc(child.getClassId())) {
            if (!a.isActiveOn(today)) continue;
            Map<String, Object> m = describe(a, today);
            m.put("done", done(mine, a));
            out.add(m);
        }
        return out;
    }

    private static Map<String, Object> describe(LearnAssignment a, LocalDate today) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("topic", a.getTopic());
        m.put("rounds", a.getRounds());
        m.put("startDate", a.getStartDate().toString());
        m.put("endDate", a.getEndDate().toString());
        boolean active = a.isActiveOn(today);
        m.put("active", active);
        m.put("daysLeft", active ? ChronoUnit.DAYS.between(today, a.getEndDate()) + 1 : 0);
        return m;
    }

    private LearnClass ownClass(LearnerProfile v, Long id) {
        LearnClass c = classes.findById(id == null ? -1L : id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "אין כיתה כזו"));
        if (access.role(v) != AccessService.Role.ADMIN && !c.getTeacherId().equals(v.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "זו לא כיתה שלך");
        }
        return c;
    }

    private static Topic topic(Object raw) {
        Topic t;
        try {
            t = Topic.valueOf(String.valueOf(raw).trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "נושא לא מוכר");
        }
        // GEOMETRY (all tracks mixed) has no map of its own - a child couldn't find where to do it.
        if (t == Topic.GEOMETRY) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "נושא לא מוכר");
        return t;
    }

    private static int number(Object raw, int dflt, int min, int max, String error) {
        if (raw == null || raw.toString().isBlank()) return dflt;
        try {
            int n = Integer.parseInt(raw.toString().trim());
            if (n >= min && n <= max) return n;
        } catch (NumberFormatException ignored) {
            // falls through to the error
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error);
    }
}
