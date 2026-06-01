package com.example.brainblocks.dto.response;

import com.example.brainblocks.model.RaceRoom;
import com.example.brainblocks.model.RaceRoomStatus;

import java.time.LocalDateTime;

public class RaceRoomResponse {

    private final String roomCode;
    private final RaceRoomStatus status;
    private final int maxPlayers;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public RaceRoomResponse(
            String roomCode,
            RaceRoomStatus status,
            int maxPlayers,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this.roomCode = roomCode;
        this.status = status;
        this.maxPlayers = maxPlayers;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static RaceRoomResponse fromEntity(RaceRoom raceRoom) {
        return new RaceRoomResponse(
                raceRoom.getRoomCode(),
                raceRoom.getStatus(),
                raceRoom.getMaxPlayers(),
                raceRoom.getCreatedAt(),
                raceRoom.getUpdatedAt()
        );
    }

    public String getRoomCode() {
        return roomCode;
    }

    public RaceRoomStatus getStatus() {
        return status;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
