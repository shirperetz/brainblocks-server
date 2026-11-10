package com.example.brainblocks.service;

import com.example.brainblocks.dto.response.RaceQuestionResponse;
import org.springframework.stereotype.Service;

@Service
public class RaceQuestionService {

    private final RaceGameService raceGameService;

    public RaceQuestionService(RaceGameService raceGameService) {
        this.raceGameService = raceGameService;
    }

    public RaceQuestionResponse getOrGenerateQuestion(String roomCode, Long participantId) {
        return raceGameService.getOrGenerateQuestion(roomCode, participantId);
    }
}
