package com.example.brainblocks.controller;

import com.example.brainblocks.dto.request.CreateRaceRoomRequest;
import com.example.brainblocks.service.RaceQuestionSettingsService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/race-rooms")
public class RaceQuestionSettingsController {

    private final RaceQuestionSettingsService settingsService;

    public RaceQuestionSettingsController(RaceQuestionSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @PutMapping("/{roomCode}/question-settings")
    public Map<String, Object> updateSettings(@PathVariable String roomCode,
                                               @RequestBody CreateRaceRoomRequest request) {
        settingsService.updateByRoomCode(roomCode,
                request.getOperations(), request.getNumberTypes(),
                request.getDifficulty(), request.getQuestionTimeLimit(), request.isManualMode());
        return Map.of("roomCode", roomCode, "updated", true);
    }
}
