package com.example.brainblocks.learning.service;

import com.example.brainblocks.learning.model.LearnClass;
import com.example.brainblocks.learning.model.LearnerProfile;
import com.example.brainblocks.learning.repository.LearnClassRepository;
import com.example.brainblocks.learning.repository.LearnerProfileRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 2026-11-11 (Shir: "teachers look at their students, I look at mine, and I oversee everything"):
 * the ONE place that decides who sees which child. Admin: every child. Teacher (approved, not
 * blocked): only the children in her own classes. A child: only itself. Anything else: 403.
 */
@Service
public class AccessService {

    private final LearnerProfileRepository profiles;
    private final LearnClassRepository classes;

    public AccessService(LearnerProfileRepository profiles, LearnClassRepository classes) {
        this.profiles = profiles;
        this.classes = classes;
    }

    public enum Role { ADMIN, TEACHER, CHILD }

    public LearnerProfile viewer(Long id) {
        return profiles.findById(id == null ? -1L : id)
                .filter(p -> !p.isDisabled() && p.isApproved())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "אין הרשאה"));
    }

    public Role role(LearnerProfile p) {
        if (p.isAdmin()) return Role.ADMIN;
        if (p.isTeacher()) return Role.TEACHER;
        return Role.CHILD;
    }

    /** The class ids a teacher owns (the admin: all of them). */
    public Set<Long> classIdsOf(LearnerProfile viewer) {
        List<LearnClass> list = role(viewer) == Role.ADMIN ? classes.findAll() : classes.findByTeacherId(viewer.getId());
        return list.stream().map(LearnClass::getId).collect(Collectors.toSet());
    }

    /** Teachers and the admin only. */
    public LearnerProfile requireStaff(Long viewerId) {
        LearnerProfile v = viewer(viewerId);
        if (role(v) == Role.CHILD) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "רק למורים ולמנהלת");
        return v;
    }

    public LearnerProfile requireAdmin(Long viewerId) {
        LearnerProfile v = viewer(viewerId);
        if (role(v) != Role.ADMIN) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "רק למנהלת");
        return v;
    }

    public boolean canSee(LearnerProfile viewer, LearnerProfile child) {
        if (child.isAdmin() || child.isTeacher()) return role(viewer) == Role.ADMIN;
        return switch (role(viewer)) {
            case ADMIN -> true;
            case TEACHER -> child.getClassId() != null && classIdsOf(viewer).contains(child.getClassId());
            case CHILD -> Objects.equals(viewer.getId(), child.getId());
        };
    }

    public void requireCanSee(Long viewerId, Long childId) {
        LearnerProfile v = viewer(viewerId);
        LearnerProfile c = profiles.findById(childId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "אין ילד כזה"));
        if (!canSee(v, c)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "הילד/ה לא בכיתה שלך");
    }

    /** The children (not teachers/admins) a staff member sees, optionally one class only. Includes waiting ones. */
    public List<LearnerProfile> visibleChildren(Long viewerId, Long classId) {
        LearnerProfile v = requireStaff(viewerId);
        boolean admin = role(v) == Role.ADMIN;
        Set<Long> mine = admin ? null : classIdsOf(v);
        if (classId != null && !admin && !mine.contains(classId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "זו לא כיתה שלך");
        }
        return profiles.findAll().stream()
                .filter(p -> !p.isAdmin() && !p.isTeacher())
                .filter(p -> admin || (p.getClassId() != null && mine.contains(p.getClassId())))
                .filter(p -> classId == null || classId.equals(p.getClassId()) || (classId == 0L && p.getClassId() == null))
                .sorted(Comparator.comparing(LearnerProfile::getName, Comparator.nullsLast(String::compareTo)))
                .toList();
    }
}
