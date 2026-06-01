package com.example.brainblocks.service;

import com.example.brainblocks.dto.request.JoinRaceRoomRequest;
import com.example.brainblocks.dto.response.RaceLobbyResponse;
import com.example.brainblocks.dto.response.RacePlayerResponse;
import com.example.brainblocks.model.RacePlayer;
import com.example.brainblocks.model.RaceRoom;
import com.example.brainblocks.model.RaceRoomStatus;
import com.example.brainblocks.repository.RacePlayerRepository;
import com.example.brainblocks.repository.RaceRoomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class RacePlayerService {

    private static final int MIN_DISPLAY_NAME_LENGTH = 1;
    private static final int MAX_DISPLAY_NAME_LENGTH = 24;

    private final RaceRoomRepository raceRoomRepository;
    private final RacePlayerRepository racePlayerRepository;

    public RacePlayerService(RaceRoomRepository raceRoomRepository, RacePlayerRepository racePlayerRepository) {
        this.raceRoomRepository = raceRoomRepository;
        this.racePlayerRepository = racePlayerRepository;
    }

    @Transactional
    public RacePlayerResponse joinRaceRoom(String roomCode, JoinRaceRoomRequest request) {
        RaceRoom raceRoom = findRaceRoom(roomCode);
        validateRoomCanAcceptPlayers(raceRoom);

        String displayName = validateDisplayName(request);
        validateDisplayNameIsAvailable(raceRoom, displayName);

        RacePlayer racePlayer = new RacePlayer(raceRoom, displayName);
        return RacePlayerResponse.fromEntity(racePlayerRepository.save(racePlayer));
    }

    @Transactional(readOnly = true)
    public RaceLobbyResponse getLobby(String roomCode) {
        RaceRoom raceRoom = findRaceRoom(roomCode);
        List<RacePlayerResponse> players = racePlayerRepository.findByRaceRoomOrderByJoinedAtAsc(raceRoom)
                .stream()
                .map(RacePlayerResponse::fromEntity)
                .toList();

        return new RaceLobbyResponse(
                raceRoom.getRoomCode(),
                raceRoom.getStatus(),
                raceRoom.getMaxPlayers(),
                players.size(),
                players
        );
    }

    private RaceRoom findRaceRoom(String roomCode) {
        return raceRoomRepository.findByRoomCode(roomCode.toUpperCase())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Race room not found"));
    }

    private void validateRoomCanAcceptPlayers(RaceRoom raceRoom) {
        if (raceRoom.getStatus() != RaceRoomStatus.WAITING) {
            throw new ResponseStatusException(CONFLICT, "Race room is not waiting for players");
        }

        if (racePlayerRepository.countByRaceRoom(raceRoom) >= raceRoom.getMaxPlayers()) {
            throw new ResponseStatusException(CONFLICT, "Race room is full");
        }
    }

    private String validateDisplayName(JoinRaceRoomRequest request) {
        if (request == null || request.getDisplayName() == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Display name is required");
        }

        String displayName = request.getDisplayName().trim();
        if (displayName.length() < MIN_DISPLAY_NAME_LENGTH) {
            throw new ResponseStatusException(BAD_REQUEST, "Display name cannot be blank");
        }

        if (displayName.length() > MAX_DISPLAY_NAME_LENGTH) {
            throw new ResponseStatusException(BAD_REQUEST, "Display name cannot be longer than 24 characters");
        }

        return displayName;
    }

    private void validateDisplayNameIsAvailable(RaceRoom raceRoom, String displayName) {
        if (racePlayerRepository.existsByRaceRoomAndDisplayNameIgnoreCase(raceRoom, displayName)) {
            throw new ResponseStatusException(CONFLICT, "Display name is already taken in this room");
        }
    }
}
