package com.example.brainblocks.controller;

import com.example.brainblocks.dto.request.AnswerRequest;
import com.example.brainblocks.dto.request.DecisionRequest;
import com.example.brainblocks.dto.response.AnswerResponse;
import com.example.brainblocks.dto.response.RaceQuestionResponse;
import com.example.brainblocks.service.RaceGameService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/race-rooms")
public class RaceQuestionController {

    private final RaceGameService raceGameService;

    public RaceQuestionController(RaceGameService raceGameService) {
        this.raceGameService = raceGameService;
    }

    @GetMapping("/{roomCode}/question")
    public RaceQuestionResponse getQuestion(@PathVariable String roomCode,
                                             @RequestParam Long participantId) {
        return raceGameService.getOrGenerateQuestion(roomCode, participantId);
    }

    @PostMapping("/{roomCode}/answer")
    public AnswerResponse submitAnswer(@PathVariable String roomCode,
                                       @RequestBody AnswerRequest request) {
        return raceGameService.submitAnswer(roomCode, request.getParticipantId(),
                request.getQuestionId(), request.getAnswer());
    }

    @PostMapping("/{roomCode}/decision")
    public RaceQuestionResponse makeDecision(@PathVariable String roomCode,
                                              @RequestBody DecisionRequest request) {
        return raceGameService.handleDecision(roomCode, request.getParticipantId(), request.getChoice());
    }
}
