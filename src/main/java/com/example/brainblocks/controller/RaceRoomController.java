package com.example.brainblocks.controller;

import com.example.brainblocks.dto.request.CreateRaceRoomRequest;
import com.example.brainblocks.dto.response.GameEventResponse;
import com.example.brainblocks.dto.response.LeaderboardEntryResponse;
import com.example.brainblocks.dto.response.RaceRoomResponse;
import com.example.brainblocks.dto.response.RaceResultsResponse;
import com.example.brainblocks.service.RaceGameService;
import com.example.brainblocks.service.RaceRoomService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/race-rooms")
public class RaceRoomController {

    private final RaceRoomService raceRoomService;
    private final RaceGameService raceGameService;

    public RaceRoomController(RaceRoomService raceRoomService, RaceGameService raceGameService) {
        this.raceRoomService = raceRoomService;
        this.raceGameService = raceGameService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RaceRoomResponse createRaceRoom(@RequestBody CreateRaceRoomRequest request) {
        return raceRoomService.createRaceRoom(request);
    }

    @GetMapping("/{roomCode}")
    public RaceRoomResponse getRaceRoom(@PathVariable String roomCode) {
        return raceRoomService.getRaceRoomByCode(roomCode);
    }

    @PostMapping("/{roomCode}/start")
    public RaceRoomResponse startRace(@PathVariable String roomCode) {
        return raceRoomService.startRace(roomCode);
    }

    @PostMapping("/{roomCode}/finish")
    public RaceResultsResponse finishRace(@PathVariable String roomCode) {
        return raceGameService.finishRace(roomCode);
    }

    @GetMapping("/{roomCode}/results")
    public RaceResultsResponse getResults(@PathVariable String roomCode) {
        return raceGameService.getResults(roomCode);
    }

    @GetMapping("/{roomCode}/leaderboard")
    public List<LeaderboardEntryResponse> getLeaderboard(@PathVariable String roomCode) {
        return raceGameService.getLeaderboard(roomCode);
    }

    @GetMapping("/{roomCode}/events-feed")
    public List<GameEventResponse> getEventsFeed(@PathVariable String roomCode) {
        return raceGameService.getRecentEvents(roomCode);
    }
}
