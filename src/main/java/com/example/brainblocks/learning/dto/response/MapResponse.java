package com.example.brainblocks.learning.dto.response;

import java.util.List;

/** The full stage map for one topic. */
public class MapResponse {

    private final String topic;
    private final List<StageDto> stages;
    private final int totalStars;

    public MapResponse(String topic, List<StageDto> stages, int totalStars) {
        this.topic = topic;
        this.stages = stages;
        this.totalStars = totalStars;
    }

    public String getTopic() { return topic; }
    public List<StageDto> getStages() { return stages; }
    public int getTotalStars() { return totalStars; }
}
