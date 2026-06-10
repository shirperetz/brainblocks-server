package com.example.brainblocks.service;

import com.example.brainblocks.dto.response.RaceQuestionResponse;
import com.example.brainblocks.model.QuestionDifficulty;
import com.example.brainblocks.model.QuestionOperation;
import com.example.brainblocks.model.RaceQuestion;
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
    private final SecureRandom random = new SecureRandom();

    public RaceQuestionService(
            RaceRoomRepository raceRoomRepository,
            RaceQuestionRepository raceQuestionRepository
    ) {
        this.raceRoomRepository = raceRoomRepository;
        this.raceQuestionRepository = raceQuestionRepository;
    }

    @Transactional
    public RaceQuestionResponse generateQuestion(String roomCode) {
        RaceRoom raceRoom = findRaceRoom(roomCode);
        validateRaceIsInProgress(raceRoom);

        RaceQuestion raceQuestion = createEasyQuestion(raceRoom);

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

    private RaceQuestion createEasyQuestion(RaceRoom raceRoom) {
        QuestionOperation operation = random.nextBoolean()
                ? QuestionOperation.ADDITION
                : QuestionOperation.SUBTRACTION;
        int firstNumber = randomEasyNumber();
        int secondNumber = randomEasyNumber();

        if (operation == QuestionOperation.SUBTRACTION && secondNumber > firstNumber) {
            int originalFirstNumber = firstNumber;
            firstNumber = secondNumber;
            secondNumber = originalFirstNumber;
        }

        int correctAnswer = operation == QuestionOperation.ADDITION
                ? firstNumber + secondNumber
                : firstNumber - secondNumber;
        String operatorSymbol = operation == QuestionOperation.ADDITION ? "+" : "-";
        String questionText = firstNumber + " " + operatorSymbol + " " + secondNumber;

        return new RaceQuestion(
                raceRoom,
                questionText,
                correctAnswer,
                operation,
                QuestionDifficulty.EASY
        );
    }

    private int randomEasyNumber() {
        return random.nextInt(MAX_EASY_NUMBER - MIN_EASY_NUMBER + 1) + MIN_EASY_NUMBER;
    }
}
