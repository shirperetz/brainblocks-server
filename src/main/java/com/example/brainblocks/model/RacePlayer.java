package com.example.brainblocks.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "race_players")
public class RacePlayer {

    private static final String[] PLAYER_COLORS = {
        "#00d9ff", "#ff4dff", "#ffd45a", "#7d5cff",
        "#00eaff", "#ff8bdc", "#4dff91", "#ff6b4d"
    };

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "race_room_id", nullable = false)
    private RaceRoom raceRoom;

    @Column(nullable = false, length = 24)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlayerStatus status;

    @Column(nullable = false, length = 12)
    private String color;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false)
    private int score;

    @Column(nullable = false)
    private int correctAnswers;

    @Column(nullable = false)
    private int wrongAnswers;

    @Column(nullable = false)
    private long totalAnswerTimeMs;

    @Column(nullable = false)
    private int currentStreak;

    @Column(nullable = false)
    private int bestStreak;

    @Column
    private Long currentQuestionId;

    @Column(nullable = false)
    private boolean pendingDecision;

    @Column(nullable = false)
    private int dirtRoadQuestionsLeft;

    @Column(nullable = false)
    private boolean shieldActive;

    @Column(nullable = false)
    private boolean doubleFuelActive;

    @Column(nullable = false)
    private int doubleFuelAnswersLeft;

    @Column(nullable = false)
    private int badLuckCount;

    @Column(nullable = false)
    private int consecutiveBadLuck;

    @Column(nullable = false)
    private int correctAnswersSinceDecision;

    @Column(nullable = false, updatable = false)
    private LocalDateTime joinedAt;

    @Column
    private LocalDateTime finishedAt;

    protected RacePlayer() {}

    public RacePlayer(RaceRoom raceRoom, String displayName, int colorIndex) {
        this.raceRoom = raceRoom;
        this.displayName = displayName;
        this.color = PLAYER_COLORS[colorIndex % PLAYER_COLORS.length];
        this.status = PlayerStatus.PENDING;
        this.position = 0;
        this.score = 0;
        this.correctAnswers = 0;
        this.wrongAnswers = 0;
        this.totalAnswerTimeMs = 0;
        this.currentStreak = 0;
        this.bestStreak = 0;
        this.pendingDecision = false;
        this.dirtRoadQuestionsLeft = 0;
        this.shieldActive = false;
        this.doubleFuelActive = false;
        this.doubleFuelAnswersLeft = 0;
        this.badLuckCount = 0;
        this.consecutiveBadLuck = 0;
        this.correctAnswersSinceDecision = 0;
    }

    public void approve() { this.status = PlayerStatus.APPROVED; }
    public void reject() { this.status = PlayerStatus.REJECTED; }

    public void addCorrectAnswer(long answerTimeMs, int basePoints) {
        this.correctAnswers++;
        this.totalAnswerTimeMs += answerTimeMs;
        this.currentStreak++;
        if (this.currentStreak > this.bestStreak) this.bestStreak = this.currentStreak;
        this.correctAnswersSinceDecision++;

        int points = basePoints;
        if (doubleFuelActive && doubleFuelAnswersLeft > 0) {
            points *= 2;
            doubleFuelAnswersLeft--;
            if (doubleFuelAnswersLeft == 0) doubleFuelActive = false;
        }
        this.score += points;
        this.position = Math.min(this.position + points, Integer.MAX_VALUE);
        this.consecutiveBadLuck = 0;
    }

    public void addWrongAnswer(long answerTimeMs) {
        this.wrongAnswers++;
        this.totalAnswerTimeMs += answerTimeMs;
        this.currentStreak = 0;
    }

    public void applyPointsDelta(int delta) {
        this.score = Math.max(0, this.score + delta);
        this.position = Math.max(0, this.position + delta);
    }

    public void activateShield() { this.shieldActive = true; }

    public boolean consumeShield() {
        if (shieldActive) { shieldActive = false; return true; }
        return false;
    }

    public void activateDoubleFuel() {
        this.doubleFuelActive = true;
        this.doubleFuelAnswersLeft = 3;
    }

    public void recordBadLuck() {
        this.badLuckCount++;
        this.consecutiveBadLuck++;
    }

    public void resetDecisionCounter() { this.correctAnswersSinceDecision = 0; }
    public void setPendingDecision(boolean pending) { this.pendingDecision = pending; }
    public void setDirtRoadQuestionsLeft(int count) { this.dirtRoadQuestionsLeft = count; }
    public void decrementDirtRoad() { if (dirtRoadQuestionsLeft > 0) dirtRoadQuestionsLeft--; }
    public void setCurrentQuestionId(Long id) { this.currentQuestionId = id; }
    public void setFinishedAt(LocalDateTime time) { this.finishedAt = time; }

    @PrePersist
    void onCreate() { joinedAt = LocalDateTime.now(); }

    public double getAccuracy() {
        int total = correctAnswers + wrongAnswers;
        return total == 0 ? 0.0 : (double) correctAnswers / total * 100.0;
    }

    public double getAverageAnswerTimeMs() {
        int total = correctAnswers + wrongAnswers;
        return total == 0 ? 0.0 : (double) totalAnswerTimeMs / total;
    }

    public Long getId() { return id; }
    public RaceRoom getRaceRoom() { return raceRoom; }
    public String getDisplayName() { return displayName; }
    public PlayerStatus getStatus() { return status; }
    public String getColor() { return color; }
    public int getPosition() { return position; }
    public int getScore() { return score; }
    public int getCorrectAnswers() { return correctAnswers; }
    public int getWrongAnswers() { return wrongAnswers; }
    public long getTotalAnswerTimeMs() { return totalAnswerTimeMs; }
    public int getCurrentStreak() { return currentStreak; }
    public int getBestStreak() { return bestStreak; }
    public Long getCurrentQuestionId() { return currentQuestionId; }
    public boolean isPendingDecision() { return pendingDecision; }
    public int getDirtRoadQuestionsLeft() { return dirtRoadQuestionsLeft; }
    public boolean isShieldActive() { return shieldActive; }
    public boolean isDoubleFuelActive() { return doubleFuelActive; }
    public int getDoubleFuelAnswersLeft() { return doubleFuelAnswersLeft; }
    public int getBadLuckCount() { return badLuckCount; }
    public int getConsecutiveBadLuck() { return consecutiveBadLuck; }
    public int getCorrectAnswersSinceDecision() { return correctAnswersSinceDecision; }
    public LocalDateTime getJoinedAt() { return joinedAt; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
}
