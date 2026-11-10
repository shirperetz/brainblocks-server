package com.example.brainblocks.controller;

import com.example.brainblocks.dto.request.JoinRaceRoomRequest;
import com.example.brainblocks.dto.response.RaceLobbyResponse;
import com.example.brainblocks.dto.response.RacePlayerResponse;
import com.example.brainblocks.service.RacePlayerService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/race-rooms")
public class RacePlayerController {

    private final RacePlayerService racePlayerService;

    public RacePlayerController(RacePlayerService racePlayerService) {
        this.racePlayerService = racePlayerService;
    }

    @PostMapping("/{roomCode}/players")
    @ResponseStatus(HttpStatus.CREATED)
    public RacePlayerResponse joinRaceRoom(@PathVariable String roomCode,
                                           @RequestBody JoinRaceRoomRequest request) {
        return racePlayerService.joinRaceRoom(roomCode, request);
    }

    @GetMapping("/{roomCode}/lobby")
    public RaceLobbyResponse getLobby(@PathVariable String roomCode) {
        return racePlayerService.getLobby(roomCode);
    }

    @GetMapping("/{roomCode}/players/{participantId}")
    public RacePlayerResponse getPlayer(@PathVariable String roomCode,
                                        @PathVariable Long participantId) {
        return racePlayerService.getPlayer(roomCode, participantId);
    }

    @PostMapping("/{roomCode}/players/{participantId}/approve")
    public RacePlayerResponse approveParticipant(@PathVariable String roomCode,
                                                  @PathVariable Long participantId) {
        return racePlayerService.approveParticipant(roomCode, participantId);
    }

    @PostMapping("/{roomCode}/players/{participantId}/reject")
    public RacePlayerResponse rejectParticipant(@PathVariable String roomCode,
                                                 @PathVariable Long participantId) {
        return racePlayerService.rejectParticipant(roomCode, participantId);
    }
}
