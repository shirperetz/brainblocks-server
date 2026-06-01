package com.example.brainblocks.controller;

import com.example.brainblocks.dto.response.RaceRoomResponse;
import com.example.brainblocks.service.RaceRoomService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin(origins = "http://localhost:5173")
public class RaceRoomController {

    private final RaceRoomService raceRoomService;

    public RaceRoomController(RaceRoomService raceRoomService) {
        this.raceRoomService = raceRoomService;
    }

    @PostMapping("/api/race-rooms")
    @ResponseStatus(HttpStatus.CREATED)
    public RaceRoomResponse createRaceRoom() {
        return raceRoomService.createRaceRoom();
    }

    @GetMapping("/api/race-rooms/{roomCode}")
    public RaceRoomResponse getRaceRoom(@PathVariable String roomCode) {
        return raceRoomService.getRaceRoomByCode(roomCode);
    }
}
