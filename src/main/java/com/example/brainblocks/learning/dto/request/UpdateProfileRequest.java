package com.example.brainblocks.learning.dto.request;

/**
 * Body for {@code PATCH /api/learn/profiles/{id}}. Every field is optional — only the
 * non-null ones are applied (partial update). Used by the grade/difficulty screen and settings.
 */
public class UpdateProfileRequest {

    private String avatar;
    private String grade;
    private String difficulty;
    private Boolean soundEnabled;

    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
    public String getGrade() { return grade; }
    public void setGrade(String grade) { this.grade = grade; }
    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }
    public Boolean getSoundEnabled() { return soundEnabled; }
    public void setSoundEnabled(Boolean soundEnabled) { this.soundEnabled = soundEnabled; }
}
