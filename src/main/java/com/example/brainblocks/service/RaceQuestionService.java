package com.example.brainblocks.service;

import com.example.brainblocks.dto.response.RaceQuestionResponse;
import com.example.brainblocks.model.QuestionDifficulty;
import com.example.brainblocks.model.QuestionOperation;
import com.example.brainblocks.model.RaceQuestion;
import com.example.brainblocks.model.RaceQuestionSettings;
import com.example.brainblocks.model.RaceRoom;
import com.example.brainblocks.model.RaceRoomStatus;
import com.example.brainblocks.repository.RaceQuestionRepository;
import com.example.brainblocks.repository.RaceRoomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class RaceQuestionService {

    private static final int MIN_EASY_NUMBER = 1;
    private static final int MAX_EASY_NUMBER = 20;

    private final RaceRoomRepository raceRoomRepository;
    private final RaceQuestionRepository raceQuestionRepository;
    private final RaceQuestionSettingsService raceQuestionSettingsService;
    private final SecureRandom random = new SecureRandom();

    public RaceQuestionService(
            RaceRoomRepository raceRoomRepository,
            RaceQuestionRepository raceQuestionRepository,
            RaceQuestionSettingsService raceQuestionSettingsService
    ) {
        this.raceRoomRepository = raceRoomRepository;
        this.raceQuestionRepository = raceQuestionRepository;
        this.raceQuestionSettingsService = raceQuestionSettingsService;
    }

    @Transactional
    public RaceQuestionResponse generateQuestion(String roomCode) {
        RaceRoom raceRoom = findRaceRoom(roomCode);
        validateRaceIsInProgress(raceRoom);
        RaceQuestionSettings settings = raceQuestionSettingsService.getSettingsOrDefault(raceRoom);

        RaceQuestion raceQuestion = createEasyQuestion(raceRoom, settings);

        return RaceQuestionResponse.fromEntity(raceQuestionRepository.save(raceQuestion));
    }

    private RaceRoom findRaceRoom(String roomCode) {
        return raceRoomRepository.findByRoomCode(roomCode.toUpperCase())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Race room not found"));
    }

    private void validateRaceIsInProgress(RaceRoom raceRoom) {
        if (raceRoom.getStatus() != RaceRoomStatus.IN_PROGRESS) {
            throw new ResponseStatusException(CONFLICT, "Race room is not in progress");
        }
    }

    private RaceQuestion createEasyQuestion(RaceRoom raceRoom, RaceQuestionSettings settings) {
        QuestionOperation operation = randomOperation(settings);
        int firstNumber = randomEasyNumber();
        int secondNumber = randomEasyNumber();

        if (operation == QuestionOperation.SUBTRACTION && secondNumber > firstNumber) {
            int originalFirstNumber = firstNumber;
            firstNumber = secondNumber;
            secondNumber = originalFirstNumber;
        }

        if (operation == QuestionOperation.DIVISION) {
            secondNumber = randomEasyNumber();
            int answer = randomEasyNumber();
            firstNumber = secondNumber * answer;
        }

        int correctAnswer = calculateAnswer(operation, firstNumber, secondNumber);
        String operatorSymbol = operatorSymbol(operation);
        String questionText = firstNumber + " " + operatorSymbol + " " + secondNumber;

        return new RaceQuestion(
                raceRoom,
                questionText,
                correctAnswer,
                operation,
                settings.getDifficulty()
        );
    }

    private QuestionOperation randomOperation(RaceQuestionSettings settings) {
        QuestionOperation[] operations = settings.getOperations().toArray(QuestionOperation[]::new);
        return operations[random.nextInt(operations.length)];
    }

    private int calculateAnswer(QuestionOperation operation, int firstNumber, int secondNumber) {
        return switch (operation) {
            case ADDITION -> firstNumber + secondNumber;
            case SUBTRACTION -> firstNumber - secondNumber;
            case MULTIPLICATION -> firstNumber * secondNumber;
            case DIVISION -> firstNumber / secondNumber;
        };
    }

    private String operatorSymbol(QuestionOperation operation) {
        return switch (operation) {
            case ADDITION -> "+";
            case SUBTRACTION -> "-";
            case MULTIPLICATION -> "*";
            case DIVISION -> "/";
        };
    }

    private int randomEasyNumber() {
        return random.nextInt(MAX_EASY_NUMBER - MIN_EASY_NUMBER + 1) + MIN_EASY_NUMBER;
    }
}
