package com.example.brainblocks.controller;

import com.example.brainblocks.dto.response.RaceQuestionResponse;
import com.example.brainblocks.service.RaceQuestionService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin(origins = "http://localhost:5173")
public class RaceQuestionController {

    private final RaceQuestionService raceQuestionService;

    public RaceQuestionController(RaceQuestionService raceQuestionService) {
        this.raceQuestionService = raceQuestionService;
    }

    @PostMapping("/api/race-rooms/{roomCode}/questions")
    @ResponseStatus(HttpStatus.CREATED)
    public RaceQuestionResponse generateQuestion(@PathVariable String roomCode) {
        return raceQuestionService.generateQuestion(roomCode);
    }
}
