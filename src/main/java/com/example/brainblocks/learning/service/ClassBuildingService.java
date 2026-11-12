package com.example.brainblocks.learning.service;

import com.example.brainblocks.learning.model.LearnClass;
import com.example.brainblocks.learning.model.LearnerProfile;
import com.example.brainblocks.learning.repository.LearnClassRepository;
import com.example.brainblocks.learning.repository.LearnerAnswerRepository;
import com.example.brainblocks.learning.repository.LearnerProfileRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 2026-11-12 (idea #6, "a shared class challenge"): the class builds one building together each week
 * (Sunday to Saturday) from everyone's right answers - 30 per child. Teamwork, not a race: a child
 * adds at most twice its share (one child can't build it alone), and nobody is ranked - only "how
 * many helped" and the child's own part. Counted from learn_answer, nothing stored.
 */
@Service
public class ClassBuildingService {

    public static final int PER_CHILD = 30;
    /** Floors of the picture: 0 empty lot, 1 foundations, 2-3 floors, 4 roof, 5 flag (done). */
    public static final int STAGES = 5;

    private final LearnClassRepository classes;
    private final LearnerProfileRepository profiles;
    private final LearnerAnswerRepository answers;
    private final AccessService access;

    public ClassBuildingService(LearnClassRepository classes, LearnerProfileRepository profiles,
                                LearnerAnswerRepository answers, AccessService access) {
        this.classes = classes;
        this.profiles = profiles;
        this.answers = answers;
        this.access = access;
    }

    /** Sunday 00:00 of this week (the Israeli school week). */
    static LocalDateTime weekStart(LocalDate today) {
        return today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY)).atStartOfDay();
    }

    /** How a week's numbers become the building. Pure, for the tests. */
    static Map<String, Object> picture(int children, List<Long> rightPerChild) {
        int target = PER_CHILD * Math.max(1, children);
        long cap = 2L * PER_CHILD;
        long built = 0;
        int helped = 0;
        for (long n : rightPerChild) {
            built += Math.min(n, cap);
            if (n > 0) helped++;
        }
        built = Math.min(built, target);
        int stage = (int) (built * STAGES / target);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("target", target);
        m.put("built", built);
        m.put("stage", stage);
        m.put("done", built >= target);
        m.put("children", children);
        m.put("helped", helped);
        return m;
    }

    public Map<String, Object> forClass(Long me, Long classId) {
        LearnerProfile v = access.requireStaff(me);
        LearnClass c = classes.findById(classId == null ? -1L : classId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "אין כיתה כזו"));
        if (access.role(v) != AccessService.Role.ADMIN && !c.getTeacherId().equals(v.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "זו לא כיתה שלך");
        }
        return build(c, null);
    }

    /** The child's class building (null when it has no class). */
    public Map<String, Object> forChild(LearnerProfile child) {
        if (child.getClassId() == null) return null;
        return classes.findById(child.getClassId()).map(c -> build(c, child.getId())).orElse(null);
    }

    private Map<String, Object> build(LearnClass c, Long childId) {
        LocalDateTime since = weekStart(LocalDate.now());
        List<LearnerProfile> kids = profiles.findAll().stream()
                .filter(p -> c.getId().equals(p.getClassId()) && !p.isTeacher() && !p.isAdmin() && p.isApproved() && !p.isDisabled())
                .toList();
        Map<Long, Long> right = new LinkedHashMap<>();
        for (LearnerProfile k : kids) {
            right.put(k.getId(), answers.countByProfileIdAndCorrectTrueAndCreatedAtGreaterThanEqual(k.getId(), since));
        }
        Map<String, Object> m = picture(kids.size(), List.copyOf(right.values()));
        m.put("className", c.getName());
        m.put("weekStart", since.toLocalDate().toString());
        if (childId != null) m.put("mine", right.getOrDefault(childId, 0L));
        return m;
    }
}
