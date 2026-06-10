package com.example.brainblocks.service;

import com.example.brainblocks.dto.response.RaceRoomResponse;
import com.example.brainblocks.model.RaceRoom;
import com.example.brainblocks.model.RaceRoomStatus;
import com.example.brainblocks.repository.RacePlayerRepository;
import com.example.brainblocks.repository.RaceRoomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class RaceRoomService {

    private static final int MAX_PLAYERS = 8;
    private static final int ROOM_CODE_LENGTH = 6;
    private static final String ROOM_CODE_CHARACTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int MAX_CODE_ATTEMPTS = 20;

    private final RaceRoomRepository raceRoomRepository;
    private final RacePlayerRepository racePlayerRepository;
    private final SecureRandom random = new SecureRandom();

    public RaceRoomService(RaceRoomRepository raceRoomRepository, RacePlayerRepository racePlayerRepository) {
        this.raceRoomRepository = raceRoomRepository;
        this.racePlayerRepository = racePlayerRepository;
    }

    @Transactional
    public RaceRoomResponse createRaceRoom() {
        String roomCode = generateUniqueRoomCode();
        RaceRoom raceRoom = new RaceRoom(roomCode, RaceRoomStatus.WAITING, MAX_PLAYERS);

        return RaceRoomResponse.fromEntity(raceRoomRepository.save(raceRoom));
    }

    @Transactional(readOnly = true)
    public RaceRoomResponse getRaceRoomByCode(String roomCode) {
        return RaceRoomResponse.fromEntity(findRaceRoom(roomCode));
    }

    @Transactional
    public RaceRoomResponse startRace(String roomCode) {
        RaceRoom raceRoom = findRaceRoom(roomCode);
        validateRaceCanStart(raceRoom);

        raceRoom.startRace();

        return RaceRoomResponse.fromEntity(raceRoom);
    }

    private RaceRoom findRaceRoom(String roomCode) {
        return raceRoomRepository.findByRoomCode(roomCode.toUpperCase())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Race room not found"));
    }

    private void validateRaceCanStart(RaceRoom raceRoom) {
        if (raceRoom.getStatus() == RaceRoomStatus.IN_PROGRESS) {
            throw new ResponseStatusException(CONFLICT, "Race room is already in progress");
        }

        if (raceRoom.getStatus() == RaceRoomStatus.FINISHED) {
            throw new ResponseStatusException(CONFLICT, "Race room is already finished");
        }

        if (raceRoom.getStatus() != RaceRoomStatus.WAITING) {
            throw new ResponseStatusException(CONFLICT, "Race room cannot be started from its current status");
        }

        if (racePlayerRepository.countByRaceRoom(raceRoom) == 0) {
            throw new ResponseStatusException(CONFLICT, "Cannot start race without players");
        }
    }

    private String generateUniqueRoomCode() {
        for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
            String roomCode = generateRoomCode();
            if (!raceRoomRepository.existsByRoomCode(roomCode)) {
                return roomCode;
            }
        }

        throw new IllegalStateException("Could not generate a unique race room code");
    }

    private String generateRoomCode() {
        StringBuilder roomCode = new StringBuilder(ROOM_CODE_LENGTH);

        for (int index = 0; index < ROOM_CODE_LENGTH; index++) {
            int characterIndex = random.nextInt(ROOM_CODE_CHARACTERS.length());
            roomCode.append(ROOM_CODE_CHARACTERS.charAt(characterIndex));
        }

        return roomCode.toString();
    }
}
