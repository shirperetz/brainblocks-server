package com.example.brainblocks.learning.dto.response;

/** One node on a topic's stage map. */
public class StageDto {

    private final int stageNumber;
    private final String status;   // LOCKED | UNLOCKED | COMPLETED
    private final int stars;
    private final int bestScorePct;

    public StageDto(int stageNumber, String status, int stars, int bestScorePct) {
        this.stageNumber = stageNumber;
        this.status = status;
        this.stars = stars;
        this.bestScorePct = bestScorePct;
    }

    public int getStageNumber() { return stageNumber; }
    public String getStatus() { return status; }
    public int getStars() { return stars; }
    public int getBestScorePct() { return bestScorePct; }
}
