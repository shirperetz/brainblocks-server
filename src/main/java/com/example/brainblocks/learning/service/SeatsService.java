package com.example.brainblocks.learning.service;

import com.example.brainblocks.learning.model.LearnClass;
import com.example.brainblocks.learning.model.LearnerProfile;
import com.example.brainblocks.learning.repository.LearnClassRepository;
import com.example.brainblocks.learning.repository.LearnerProfileRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 2026-11-13 (Shir: "I decide how many students a class has, and how many a teacher has - one child
 * more can't get in, it blocks him at once"). The ONE place that says whether there is a seat:
 * the class's limit and the teacher's limit (over all her classes), both set by the admin only.
 * At sign-up (and asking to join) the children still waiting count too, so a full class can't even
 * be signed up to; approving / moving counts the approved children.
 */
@Service
public class SeatsService {

    private final LearnerProfileRepository profiles;
    private final LearnClassRepository classes;

    public SeatsService(LearnerProfileRepository profiles, LearnClassRepository classes) {
        this.profiles = profiles;
        this.classes = classes;
    }

    private static boolean isChild(LearnerProfile p) {
        return !p.isTeacher() && !p.isAdmin() && !p.isDisabled();
    }

    private long count(Set<Long> classIds, boolean withWaiting, Long except) {
        return profiles.findAll().stream()
                .filter(p -> isChild(p) && p.getClassId() != null && classIds.contains(p.getClassId())
                        && (withWaiting || p.isApproved()) && !Objects.equals(p.getId(), except))
                .count();
    }

    /** How many (approved) children a teacher has across all her classes. */
    public long ofTeacher(Long teacherId) {
        return count(teacherClasses(teacherId), false, null);
    }

    private Set<Long> teacherClasses(Long teacherId) {
        return classes.findByTeacherId(teacherId).stream().map(LearnClass::getId).collect(Collectors.toSet());
    }

    /**
     * Throws 409 when the class or its teacher is full. withWaiting: at sign-up / asking to join.
     * except: the child being approved / moved (not counted against herself).
     */
    public void requireSeat(LearnClass c, Long except, boolean withWaiting) {
        if (c == null) return;
        if (c.getMaxStudents() != null && count(Set.of(c.getId()), withWaiting, except) >= c.getMaxStudents()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "הכיתה " + c.getName() + " מלאה (" + c.getMaxStudents() + " תלמידים). רק המנהלת יכולה להגדיל אותה");
        }
        LearnerProfile t = profiles.findById(c.getTeacherId()).orElse(null);
        if (t != null && t.getMaxStudents() != null
                && count(teacherClasses(t.getId()), withWaiting, except) >= t.getMaxStudents()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "למורה " + t.getName() + " כבר יש " + t.getMaxStudents() + " תלמידים - המקסימום. רק המנהלת יכולה להגדיל");
        }
    }
}
