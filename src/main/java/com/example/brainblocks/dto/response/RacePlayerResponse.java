package com.example.brainblocks.dto.response;

import com.example.brainblocks.model.RacePlayer;

import java.time.LocalDateTime;

public class RacePlayerResponse {

    private final Long id;
    private final String displayName;
    private final LocalDateTime joinedAt;

    public RacePlayerResponse(Long id, String displayName, LocalDateTime joinedAt) {
        this.id = id;
        this.displayName = displayName;
        this.joinedAt = joinedAt;
    }

    public static RacePlayerResponse fromEntity(RacePlayer racePlayer) {
        return new RacePlayerResponse(
                racePlayer.getId(),
                racePlayer.getDisplayName(),
                racePlayer.getJoinedAt()
        );
    }

    public Long getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public LocalDateTime getJoinedAt() {
        return joinedAt;
    }
}
