package com.example.brainblocks.controller;

import com.example.brainblocks.dto.request.JoinRaceRoomRequest;
import com.example.brainblocks.dto.response.RaceLobbyResponse;
import com.example.brainblocks.dto.response.RacePlayerResponse;
import com.example.brainblocks.service.RacePlayerService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin(origins = "http://localhost:5173")
public class RacePlayerController {

    private final RacePlayerService racePlayerService;

    public RacePlayerController(RacePlayerService racePlayerService) {
        this.racePlayerService = racePlayerService;
    }

    @PostMapping("/api/race-rooms/{roomCode}/players")
    @ResponseStatus(HttpStatus.CREATED)
    public RacePlayerResponse joinRaceRoom(
            @PathVariable String roomCode,
            @RequestBody JoinRaceRoomRequest request
    ) {
        return racePlayerService.joinRaceRoom(roomCode, request);
    }

    @GetMapping("/api/race-rooms/{roomCode}/lobby")
    public RaceLobbyResponse getLobby(@PathVariable String roomCode) {
        return racePlayerService.getLobby(roomCode);
    }
}
