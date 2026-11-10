package com.example.brainblocks.dto.response;

import com.example.brainblocks.model.RaceRoomStatus;

import java.util.List;

public class RaceLobbyResponse {

    private final String roomCode;
    private final String name;
    private final RaceRoomStatus status;
    private final int maxPlayers;
    private final int minPlayers;
    private final int trackLength;
    private final boolean requiresApproval;
    private final int currentPlayers;
    private final List<RacePlayerResponse> players;

    public RaceLobbyResponse(String roomCode, String name, RaceRoomStatus status,
                              int maxPlayers, int minPlayers, int trackLength,
                              boolean requiresApproval, int currentPlayers,
                              List<RacePlayerResponse> players) {
        this.roomCode = roomCode;
        this.name = name;
        this.status = status;
        this.maxPlayers = maxPlayers;
        this.minPlayers = minPlayers;
        this.trackLength = trackLength;
        this.requiresApproval = requiresApproval;
        this.currentPlayers = currentPlayers;
        this.players = players;
    }

    public String getRoomCode() { return roomCode; }
    public String getName() { return name; }
    public RaceRoomStatus getStatus() { return status; }
    public int getMaxPlayers() { return maxPlayers; }
    public int getMinPlayers() { return minPlayers; }
    public int getTrackLength() { return trackLength; }
    public boolean isRequiresApproval() { return requiresApproval; }
    public int getCurrentPlayers() { return currentPlayers; }
    public List<RacePlayerResponse> getPlayers() { return players; }
}
