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
        );
    }

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
