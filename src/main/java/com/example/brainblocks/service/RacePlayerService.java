package com.example.brainblocks.service;

import com.example.brainblocks.dto.request.JoinRaceRoomRequest;
import com.example.brainblocks.dto.response.RaceLobbyResponse;
import com.example.brainblocks.dto.response.RacePlayerResponse;
import com.example.brainblocks.model.PlayerStatus;
import com.example.brainblocks.model.RacePlayer;
import com.example.brainblocks.model.RaceRoom;
import com.example.brainblocks.model.RaceRoomStatus;
import com.example.brainblocks.repository.RacePlayerRepository;
import com.example.brainblocks.repository.RaceRoomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class RacePlayerService {

    private static final int MIN_NAME_LEN = 1;
    private static final int MAX_NAME_LEN = 24;

    private final RaceRoomRepository raceRoomRepository;
    private final RacePlayerRepository racePlayerRepository;
    private final SseService sseService;

    public RacePlayerService(RaceRoomRepository raceRoomRepository, RacePlayerRepository racePlayerRepository,
                             SseService sseService) {
        this.raceRoomRepository = raceRoomRepository;
        this.racePlayerRepository = racePlayerRepository;
        this.sseService = sseService;
    }

    @Transactional
    public RacePlayerResponse joinRaceRoom(String roomCode, JoinRaceRoomRequest request) {
        RaceRoom room = findRoom(roomCode);
        validateRoomAcceptsPlayers(room);

        String name = validateName(request);
        validateNameAvailable(room, name);

        int colorIndex = racePlayerRepository.countByRaceRoom(room);
        RacePlayer player = new RacePlayer(room, name, colorIndex);
        player = racePlayerRepository.save(player);

        Map<String, Object> data = new HashMap<>();
        data.put("participantId", player.getId());
        data.put("displayName", player.getDisplayName());
        data.put("color", player.getColor());
        data.put("status", player.getStatus().name());
        sseService.broadcast(roomCode, "PARTICIPANT_JOINED", data);

        return RacePlayerResponse.fromEntity(player);
    }

    @Transactional(readOnly = true)
    public RaceLobbyResponse getLobby(String roomCode) {
        RaceRoom room = findRoom(roomCode);
        List<RacePlayerResponse> players = racePlayerRepository.findByRaceRoomOrderByJoinedAtAsc(room)
                .stream().map(RacePlayerResponse::fromEntity).toList();
        return new RaceLobbyResponse(
                room.getRoomCode(), room.getName(), room.getStatus(),
                room.getMaxPlayers(), room.getMinPlayers(), room.getTrackLength(),
                room.isRequiresApproval(), players.size(), players
        );
    }

    @Transactional(readOnly = true)
    public RacePlayerResponse getPlayer(String roomCode, Long participantId) {
        RaceRoom room = findRoom(roomCode);
        RacePlayer player = racePlayerRepository.findByRaceRoomAndId(room, participantId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Participant not found"));
        return RacePlayerResponse.fromEntity(player);
    }

    @Transactional
    public RacePlayerResponse approveParticipant(String roomCode, Long participantId) {
        RaceRoom room = findRoom(roomCode);
        RacePlayer player = findPlayer(room, participantId);
        if (player.getStatus() != PlayerStatus.PENDING) {
            throw new ResponseStatusException(CONFLICT, "Player is not in PENDING state");
        }
        player.approve();
        player = racePlayerRepository.save(player);

        Map<String, Object> data = new HashMap<>();
        data.put("participantId", player.getId());
        data.put("displayName", player.getDisplayName());
        data.put("status", "APPROVED");
        sseService.broadcast(roomCode, "PARTICIPANT_APPROVED", data);

        return RacePlayerResponse.fromEntity(player);
    }

    @Transactional
    public RacePlayerResponse rejectParticipant(String roomCode, Long participantId) {
        RaceRoom room = findRoom(roomCode);
        RacePlayer player = findPlayer(room, participantId);
        player.reject();
        player = racePlayerRepository.save(player);

        Map<String, Object> data = new HashMap<>();
        data.put("participantId", player.getId());
        data.put("displayName", player.getDisplayName());
        data.put("status", "REJECTED");
        sseService.broadcast(roomCode, "PARTICIPANT_REJECTED", data);

        return RacePlayerResponse.fromEntity(player);
    }

    private RaceRoom findRoom(String roomCode) {
        return raceRoomRepository.findByRoomCode(roomCode.toUpperCase())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Race room not found"));
    }

    private RacePlayer findPlayer(RaceRoom room, Long id) {
        return racePlayerRepository.findByRaceRoomAndId(room, id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Participant not found"));
    }

    private void validateRoomAcceptsPlayers(RaceRoom room) {
        if (room.getStatus() != RaceRoomStatus.WAITING) {
            throw new ResponseStatusException(CONFLICT, "Race room is not accepting players");
        }
        if (racePlayerRepository.countByRaceRoom(room) >= room.getMaxPlayers()) {
            throw new ResponseStatusException(CONFLICT, "Race room is full");
        }
    }

    private String validateName(JoinRaceRoomRequest request) {
        if (request == null || request.getDisplayName() == null)
            throw new ResponseStatusException(BAD_REQUEST, "Display name is required");
        String name = request.getDisplayName().trim();
        if (name.length() < MIN_NAME_LEN)
            throw new ResponseStatusException(BAD_REQUEST, "Display name cannot be blank");
        if (name.length() > MAX_NAME_LEN)
            throw new ResponseStatusException(BAD_REQUEST, "Display name too long (max 24 chars)");
        return name;
    }

    private void validateNameAvailable(RaceRoom room, String name) {
        if (racePlayerRepository.existsByRaceRoomAndDisplayNameIgnoreCase(room, name))
            throw new ResponseStatusException(CONFLICT, "Display name already taken in this room");
    }
}
