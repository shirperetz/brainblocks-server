package com.example.brainblocks.dto.response;

import com.example.brainblocks.model.RaceRoom;
import com.example.brainblocks.model.RaceRoomStatus;

import java.time.LocalDateTime;

public class RaceRoomResponse {

    private final String roomCode;
    private final String name;
    private final RaceRoomStatus status;
    private final int maxPlayers;
    private final int minPlayers;
    private final int trackLength;
    private final boolean requiresApproval;
    private final LocalDateTime createdAt;
    private final LocalDateTime startedAt;
    private final LocalDateTime finishedAt;

    public RaceRoomResponse(String roomCode, String name, RaceRoomStatus status, int maxPlayers,
                             int minPlayers, int trackLength, boolean requiresApproval,
                             LocalDateTime createdAt, LocalDateTime startedAt, LocalDateTime finishedAt) {
        this.roomCode = roomCode;
        this.name = name;
        this.status = status;
        this.maxPlayers = maxPlayers;
        this.minPlayers = minPlayers;
        this.trackLength = trackLength;
        this.requiresApproval = requiresApproval;
        this.createdAt = createdAt;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
    }

    public static RaceRoomResponse fromEntity(RaceRoom raceRoom) {
        return new RaceRoomResponse(
                raceRoom.getRoomCode(),
                raceRoom.getName(),
                raceRoom.getStatus(),
                raceRoom.getMaxPlayers(),
                raceRoom.getMinPlayers(),
                raceRoom.getTrackLength(),
                raceRoom.isRequiresApproval(),
                raceRoom.getCreatedAt(),
                raceRoom.getStartedAt(),
                raceRoom.getFinishedAt()
        );
    }

    public String getRoomCode() { return roomCode; }
    public String getName() { return name; }
    public RaceRoomStatus getStatus() { return status; }
    public int getMaxPlayers() { return maxPlayers; }
    public int getMinPlayers() { return minPlayers; }
    public int getTrackLength() { return trackLength; }
    public boolean isRequiresApproval() { return requiresApproval; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
}
