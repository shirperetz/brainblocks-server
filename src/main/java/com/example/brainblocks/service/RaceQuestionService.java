package com.example.brainblocks.service;

import com.example.brainblocks.dto.request.SubmitAnswerRequest;
import com.example.brainblocks.dto.response.RaceQuestionResponse;
import com.example.brainblocks.dto.response.SubmitAnswerResponse;
import com.example.brainblocks.model.QuestionDifficulty;
import com.example.brainblocks.model.QuestionOperation;
import com.example.brainblocks.model.RacePlayer;
import com.example.brainblocks.model.RaceQuestion;
import com.example.brainblocks.model.RaceQuestionSettings;
import com.example.brainblocks.model.RaceRoom;
import com.example.brainblocks.model.RaceRoomStatus;
import com.example.brainblocks.repository.RacePlayerRepository;
import com.example.brainblocks.repository.RaceQuestionRepository;
import com.example.brainblocks.repository.RaceRoomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class RaceQuestionService {

    private static final int MIN_EASY_NUMBER = 1;
    private static final int MAX_EASY_NUMBER = 20;

    private final RaceRoomRepository raceRoomRepository;
    private final RaceQuestionRepository raceQuestionRepository;
    private final RacePlayerRepository racePlayerRepository;
    private final RaceQuestionSettingsService raceQuestionSettingsService;
    private final SecureRandom random = new SecureRandom();

    public RaceQuestionService(
            RaceRoomRepository raceRoomRepository,
            RaceQuestionRepository raceQuestionRepository,
            RacePlayerRepository racePlayerRepository,
            RaceQuestionSettingsService raceQuestionSettingsService
    ) {
        this.raceRoomRepository = raceRoomRepository;
        this.raceQuestionRepository = raceQuestionRepository;
        this.racePlayerRepository = racePlayerRepository;
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

    @Transactional(readOnly = true)
    public SubmitAnswerResponse submitAnswer(
            String roomCode,
            Long questionId,
            SubmitAnswerRequest request
    ) {
        RaceRoom raceRoom = findRaceRoom(roomCode);
        validateRaceIsInProgress(raceRoom);
        RaceQuestion raceQuestion = findQuestion(questionId);
        validateQuestionBelongsToRoom(raceQuestion, raceRoom);
        RacePlayer racePlayer = findPlayer(validatePlayerId(request));
        validatePlayerBelongsToRoom(racePlayer, raceRoom);

        int submittedAnswer = validateSubmittedAnswer(request);
        boolean correct = submittedAnswer == raceQuestion.getCorrectAnswer();

        return new SubmitAnswerResponse(
                raceQuestion.getId(),
                racePlayer.getId(),
                submittedAnswer,
                correct
        );
    }

    private RaceRoom findRaceRoom(String roomCode) {
        return raceRoomRepository.findByRoomCode(roomCode.toUpperCase())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Race room not found"));
    }

    private RaceQuestion findQuestion(Long questionId) {
        if (questionId == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Question id is required");
        }

        return raceQuestionRepository.findById(questionId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Race question not found"));
    }

    private RacePlayer findPlayer(Long playerId) {
        return racePlayerRepository.findById(playerId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Race player not found"));
    }

    private void validateRaceIsInProgress(RaceRoom raceRoom) {
        if (raceRoom.getStatus() != RaceRoomStatus.IN_PROGRESS) {
            throw new ResponseStatusException(CONFLICT, "Race room is not in progress");
        }
    }

    private Long validatePlayerId(SubmitAnswerRequest request) {
        if (request == null || request.getPlayerId() == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Player id is required");
        }

        return request.getPlayerId();
    }

    private int validateSubmittedAnswer(SubmitAnswerRequest request) {
        if (request == null || request.getAnswer() == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Answer is required");
        }

        return request.getAnswer();
    }

    private void validateQuestionBelongsToRoom(RaceQuestion raceQuestion, RaceRoom raceRoom) {
        if (!raceQuestion.getRaceRoom().getId().equals(raceRoom.getId())) {
            throw new ResponseStatusException(BAD_REQUEST, "Question does not belong to this room");
        }
    }

    private void validatePlayerBelongsToRoom(RacePlayer racePlayer, RaceRoom raceRoom) {
        if (!racePlayer.getRaceRoom().getId().equals(raceRoom.getId())) {
            throw new ResponseStatusException(BAD_REQUEST, "Player does not belong to this room");
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
