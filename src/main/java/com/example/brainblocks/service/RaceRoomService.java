package com.example.brainblocks.service;

import com.example.brainblocks.dto.response.RaceRoomResponse;
import com.example.brainblocks.model.RaceRoom;
import com.example.brainblocks.model.RaceRoomStatus;
import com.example.brainblocks.repository.RaceRoomRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class RaceRoomService {

    private static final int MAX_PLAYERS = 8;
    private static final int ROOM_CODE_LENGTH = 6;
    private static final String ROOM_CODE_CHARACTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int MAX_CODE_ATTEMPTS = 20;

    private final RaceRoomRepository raceRoomRepository;
    private final SecureRandom random = new SecureRandom();

    public RaceRoomService(RaceRoomRepository raceRoomRepository) {
        this.raceRoomRepository = raceRoomRepository;
    }

    public RaceRoomResponse createRaceRoom() {
        String roomCode = generateUniqueRoomCode();
        RaceRoom raceRoom = new RaceRoom(roomCode, RaceRoomStatus.WAITING, MAX_PLAYERS);

        return RaceRoomResponse.fromEntity(raceRoomRepository.save(raceRoom));
    }

    public RaceRoomResponse getRaceRoomByCode(String roomCode) {
        return raceRoomRepository.findByRoomCode(roomCode.toUpperCase())
                .map(RaceRoomResponse::fromEntity)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Race room not found"));
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
