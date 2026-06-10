package com.example.brainblocks.controller;

import com.example.brainblocks.dto.request.UpdateQuestionSettingsRequest;
import com.example.brainblocks.dto.response.QuestionSettingsResponse;
import com.example.brainblocks.service.RaceQuestionSettingsService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin(origins = "http://localhost:5173")
public class RaceQuestionSettingsController {

    private final RaceQuestionSettingsService raceQuestionSettingsService;

    public RaceQuestionSettingsController(RaceQuestionSettingsService raceQuestionSettingsService) {
        this.raceQuestionSettingsService = raceQuestionSettingsService;
    }

    @PutMapping("/api/race-rooms/{roomCode}/question-settings")
    public QuestionSettingsResponse updateQuestionSettings(
            @PathVariable String roomCode,
            @RequestBody UpdateQuestionSettingsRequest request
    ) {
        return raceQuestionSettingsService.updateQuestionSettings(roomCode, request);
    }
}
