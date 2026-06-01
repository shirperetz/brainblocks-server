package com.example.brainblocks.dto.response;

import com.example.brainblocks.model.RaceRoomStatus;

import java.util.List;

public class RaceLobbyResponse {

    private final String roomCode;
    private final RaceRoomStatus status;
    private final int maxPlayers;
    private final int currentPlayers;
    private final List<RacePlayerResponse> players;

    public RaceLobbyResponse(
            String roomCode,
            RaceRoomStatus status,
            int maxPlayers,
            int currentPlayers,
            List<RacePlayerResponse> players
    ) {
        this.roomCode = roomCode;
        this.status = status;
        this.maxPlayers = maxPlayers;
        this.currentPlayers = currentPlayers;
        this.players = players;
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

    public int getCurrentPlayers() {
        return currentPlayers;
    }

    public List<RacePlayerResponse> getPlayers() {
        return players;
    }
}
