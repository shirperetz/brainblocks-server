package com.example.brainblocks.learning.dto.response;

import com.example.brainblocks.learning.model.Avatar;
import com.example.brainblocks.learning.model.Difficulty;
import com.example.brainblocks.learning.model.Grade;
import com.example.brainblocks.learning.model.LearnerProfile;

import java.time.LocalDateTime;

/**
 * Public view of a {@link LearnerProfile} returned to the kids client.
 */
public class ProfileResponse {

    private final Long id;
    private final String name;
    private final Avatar avatar;
    private final Grade grade;
    private final Difficulty difficulty;
    private final int totalPoints;
    private final int dailyStreak;
    private final boolean soundEnabled;
    private final LocalDateTime createdAt;

    public ProfileResponse(Long id, String name, Avatar avatar, Grade grade, Difficulty difficulty,
                           int totalPoints, int dailyStreak, boolean soundEnabled, LocalDateTime createdAt) {
        this.id = id;
        this.name = name;
        this.avatar = avatar;
        this.grade = grade;
        this.difficulty = difficulty;
        this.totalPoints = totalPoints;
        this.dailyStreak = dailyStreak;
        this.soundEnabled = soundEnabled;
        this.createdAt = createdAt;
    }

    public static ProfileResponse fromEntity(LearnerProfile p) {
        return new ProfileResponse(
                p.getId(), p.getName(), p.getAvatar(), p.getGrade(), p.getDifficulty(),
                p.getTotalPoints(), p.getDailyStreak(), p.isSoundEnabled(), p.getCreatedAt()
        ).withUsername(p.getUsername()).withAdmin(p.isAdmin()).withApproved(p.isApproved())
                .withTeacher(p.isTeacher(), p.getClassId()).withCompanion(p.getCompanion()).withGuidesSeen(p.getGuidesSeen()).withAdvanced(p.getAdvancedLevels())
                .withGradeLocked(p.isGradeLocked()).withGradeChangeAllowed(p.isGradeChangeAllowed());
    }

    /** 2026-11-12: the grade was set by the admin/teacher - the child can't change it. */
    private boolean gradeLocked;

    private ProfileResponse withGradeLocked(boolean locked) {
        this.gradeLocked = locked;
        return this;
    }

    public boolean isGradeLocked() { return gradeLocked; }

    /** 2026-11-12: the admin allowed this child to change its own grade (otherwise it can't). */
    private boolean gradeChangeAllowed;

    private ProfileResponse withGradeChangeAllowed(boolean allowed) {
        this.gradeChangeAllowed = allowed;
        return this;
    }

    public boolean isGradeChangeAllowed() { return gradeChangeAllowed; }

    /** 2026-11-11: the companion animal (null = default). */
    private String companion;

    private ProfileResponse withCompanion(String companion) {
        this.companion = companion;
        return this;
    }

    public String getCompanion() { return companion; }

    /** 2026-11-19: screens whose dolphin tour this child already saw. */
    private java.util.List<String> guidesSeen = java.util.List.of();

    private ProfileResponse withGuidesSeen(java.util.List<String> keys) {
        this.guidesSeen = keys;
        return this;
    }

    public java.util.List<String> getGuidesSeen() { return guidesSeen; }

    /** 2026-11-21: topic -> the higher grade a teacher approved for this child ("FRACTIONS": "G6"). */
    private java.util.Map<String, String> advanced = java.util.Map.of();

    private ProfileResponse withAdvanced(java.util.Map<com.example.brainblocks.learning.model.Topic, com.example.brainblocks.learning.model.Grade> m) {
        java.util.Map<String, String> out = new java.util.LinkedHashMap<>();
        m.forEach((t, g) -> out.put(t.name(), g.name()));
        this.advanced = out;
        return this;
    }

    public java.util.Map<String, String> getAdvanced() { return advanced; }

    /** 2026-11-11: a teacher sees the teachers' screen; a child's class (null = Shir's own). */
    private boolean teacher;
    private Long classId;

    private ProfileResponse withTeacher(boolean teacher, Long classId) {
        this.teacher = teacher;
        this.classId = classId;
        return this;
    }

    public boolean isTeacher() { return teacher; }
    public Long getClassId() { return classId; }

    /** 2026-11-11: shown on the home screen ("מחובר/ת בתור ...") - never the password or token. */
    private String username;

    private ProfileResponse withUsername(String username) {
        this.username = username;
        return this;
    }

    public String getUsername() { return username; }

    private boolean admin;

    private ProfileResponse withAdmin(boolean admin) {
        this.admin = admin;
        return this;
    }

    public boolean isAdmin() { return admin; }

    private boolean approved = true;

    private ProfileResponse withApproved(boolean approved) {
        this.approved = approved;
        return this;
    }

    public boolean isApproved() { return approved; }

    public Long getId() { return id; }
    public String getName() { return name; }
    public Avatar getAvatar() { return avatar; }
    public Grade getGrade() { return grade; }
    public Difficulty getDifficulty() { return difficulty; }
    public int getTotalPoints() { return totalPoints; }
    public int getDailyStreak() { return dailyStreak; }
    public boolean isSoundEnabled() { return soundEnabled; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
