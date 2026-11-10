package com.example.brainblocks.learning.dto.response;

import java.util.List;

/** The end-of-stage summary returned after submitting a session. */
public class SessionSummaryResponse {

    private final int correct;
    private final int total;
    private final int scorePct;
    private final int points;
    private final int stars;
    private final boolean passed;
    private final boolean nextStageUnlocked;
    private final int nextStage;          // 0 when there is no next stage to unlock
    private final int totalPoints;        // profile total after this session
    private final String encouragement;   // Hebrew message for the summary header
    private final List<MistakeDto> mistakes;
    private final List<String> newAchievements;

    public SessionSummaryResponse(int correct, int total, int scorePct, int points, int stars,
                                  boolean passed, boolean nextStageUnlocked, int nextStage,
                                  int totalPoints, String encouragement,
                                  List<MistakeDto> mistakes, List<String> newAchievements) {
        this.correct = correct;
        this.total = total;
        this.scorePct = scorePct;
        this.points = points;
        this.stars = stars;
        this.passed = passed;
        this.nextStageUnlocked = nextStageUnlocked;
        this.nextStage = nextStage;
        this.totalPoints = totalPoints;
        this.encouragement = encouragement;
        this.mistakes = mistakes;
        this.newAchievements = newAchievements;
    }

    public int getCorrect() { return correct; }
    public int getTotal() { return total; }
    public int getScorePct() { return scorePct; }
    public int getPoints() { return points; }
    public int getStars() { return stars; }
    public boolean isPassed() { return passed; }
    public boolean isNextStageUnlocked() { return nextStageUnlocked; }
    public int getNextStage() { return nextStage; }
    public int getTotalPoints() { return totalPoints; }
    public String getEncouragement() { return encouragement; }
    public List<MistakeDto> getMistakes() { return mistakes; }
    public List<String> getNewAchievements() { return newAchievements; }
}
