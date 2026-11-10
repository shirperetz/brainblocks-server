package com.example.brainblocks.service;

import com.example.brainblocks.dto.request.CreateRaceRoomRequest;
import com.example.brainblocks.dto.response.RaceRoomResponse;
import com.example.brainblocks.model.PlayerStatus;
import com.example.brainblocks.model.RaceRoom;
import com.example.brainblocks.model.RaceRoomStatus;
import com.example.brainblocks.repository.RacePlayerRepository;
import com.example.brainblocks.repository.RaceRoomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class RaceRoomService {

    private static final int ABSOLUTE_MAX_PLAYERS = 8;
    private static final int ROOM_CODE_LENGTH = 6;
    private static final String ROOM_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int MAX_CODE_ATTEMPTS = 20;

    private final RaceRoomRepository raceRoomRepository;
    private final RacePlayerRepository racePlayerRepository;
    private final RaceQuestionSettingsService settingsService;
    private final SseService sseService;
    private final SecureRandom random = new SecureRandom();

    public RaceRoomService(RaceRoomRepository raceRoomRepository, RacePlayerRepository racePlayerRepository,
                           RaceQuestionSettingsService settingsService, SseService sseService) {
        this.raceRoomRepository = raceRoomRepository;
        this.racePlayerRepository = racePlayerRepository;
        this.settingsService = settingsService;
        this.sseService = sseService;
    }

    @Transactional
    public RaceRoomResponse createRaceRoom(CreateRaceRoomRequest request) {
        String roomCode = generateUniqueRoomCode();

        int maxPlayers = (request.getMaxPlayers() > 0 && request.getMaxPlayers() <= ABSOLUTE_MAX_PLAYERS)
                ? request.getMaxPlayers() : ABSOLUTE_MAX_PLAYERS;
        int minPlayers = (request.getMinPlayers() > 0) ? request.getMinPlayers() : 1;
        int trackLength = (request.getTrackLength() > 0) ? request.getTrackLength() : 1000;
        String name = (request.getName() != null && !request.getName().isBlank()) ? request.getName() : "Math Race";
        String teacherKey = request.getTeacherKey();

        RaceRoom room = new RaceRoom(roomCode, name, teacherKey, RaceRoomStatus.WAITING,
                maxPlayers, minPlayers, trackLength, request.isRequiresApproval());
        room = raceRoomRepository.save(room);

        settingsService.createOrUpdate(room,
                request.getOperations(),
                request.getNumberTypes(),
                request.getDifficulty(),
                request.getQuestionTimeLimit(),
                request.isManualMode());

        return RaceRoomResponse.fromEntity(room);
    }

    @Transactional(readOnly = true)
    public RaceRoomResponse getRaceRoomByCode(String roomCode) {
        return RaceRoomResponse.fromEntity(findRoom(roomCode));
    }

    @Transactional
    public RaceRoomResponse startRace(String roomCode) {
        RaceRoom room = findRoom(roomCode);

        if (room.getStatus() != RaceRoomStatus.WAITING) {
            throw new ResponseStatusException(CONFLICT, "Race room cannot be started from its current status");
        }

        int approvedCount = racePlayerRepository.countByRaceRoomAndStatus(room, PlayerStatus.APPROVED);
        int totalCount = racePlayerRepository.countByRaceRoom(room);

        if (!room.isRequiresApproval() && totalCount == 0) {
            throw new ResponseStatusException(CONFLICT, "Cannot start race without players");
        }
        if (room.isRequiresApproval() && approvedCount < room.getMinPlayers()) {
            throw new ResponseStatusException(CONFLICT,
                    "Need at least " + room.getMinPlayers() + " approved player(s) to start");
        }
        if (!room.isRequiresApproval() && totalCount < room.getMinPlayers()) {
            throw new ResponseStatusException(CONFLICT,
                    "Need at least " + room.getMinPlayers() + " player(s) to start");
        }

        if (!room.isRequiresApproval()) {
            racePlayerRepository.findByRaceRoomOrderByJoinedAtAsc(room)
                    .forEach(p -> { p.approve(); racePlayerRepository.save(p); });
        }

        room.startRace();
        room = raceRoomRepository.save(room);

        Map<String, Object> data = new HashMap<>();
        data.put("roomCode", room.getRoomCode());
        data.put("status", "IN_PROGRESS");
        sseService.broadcast(roomCode, "RACE_STARTED", data);

        return RaceRoomResponse.fromEntity(room);
    }

    @Transactional
    public RaceRoomResponse finishRaceManually(String roomCode) {
        RaceRoom room = findRoom(roomCode);
        if (room.getStatus() == RaceRoomStatus.FINISHED) {
            return RaceRoomResponse.fromEntity(room);
        }
        room.finishRace();
        return RaceRoomResponse.fromEntity(raceRoomRepository.save(room));
    }

    private RaceRoom findRoom(String roomCode) {
        return raceRoomRepository.findByRoomCode(roomCode.toUpperCase())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Race room not found"));
    }

    private String generateUniqueRoomCode() {
        for (int i = 0; i < MAX_CODE_ATTEMPTS; i++) {
            String code = generateRoomCode();
            if (!raceRoomRepository.existsByRoomCode(code)) return code;
        }
        throw new IllegalStateException("Could not generate unique room code");
    }

    private String generateRoomCode() {
        StringBuilder sb = new StringBuilder(ROOM_CODE_LENGTH);
        for (int i = 0; i < ROOM_CODE_LENGTH; i++) {
            sb.append(ROOM_CODE_CHARS.charAt(random.nextInt(ROOM_CODE_CHARS.length())));
        }
        return sb.toString();
    }
}
