package com.example.brainblocks.service;

import com.example.brainblocks.dto.request.UpdateQuestionSettingsRequest;
import com.example.brainblocks.dto.response.QuestionSettingsResponse;
import com.example.brainblocks.model.QuestionDifficulty;
import com.example.brainblocks.model.QuestionNumberType;
import com.example.brainblocks.model.QuestionOperation;
import com.example.brainblocks.model.RaceQuestionSettings;
import com.example.brainblocks.model.RaceRoom;
import com.example.brainblocks.repository.RaceQuestionSettingsRepository;
import com.example.brainblocks.repository.RaceRoomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.EnumSet;
import java.util.Set;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class RaceQuestionSettingsService {

    public static final Set<QuestionOperation> DEFAULT_OPERATIONS = EnumSet.of(
            QuestionOperation.ADDITION,
            QuestionOperation.SUBTRACTION
    );
    public static final Set<QuestionNumberType> DEFAULT_NUMBER_TYPES = EnumSet.of(QuestionNumberType.WHOLE_NUMBERS);
    public static final QuestionDifficulty DEFAULT_DIFFICULTY = QuestionDifficulty.EASY;

    private final RaceRoomRepository raceRoomRepository;
    private final RaceQuestionSettingsRepository raceQuestionSettingsRepository;

    public RaceQuestionSettingsService(
            RaceRoomRepository raceRoomRepository,
            RaceQuestionSettingsRepository raceQuestionSettingsRepository
    ) {
        this.raceRoomRepository = raceRoomRepository;
        this.raceQuestionSettingsRepository = raceQuestionSettingsRepository;
    }

    @Transactional
    public QuestionSettingsResponse updateQuestionSettings(
            String roomCode,
            UpdateQuestionSettingsRequest request
    ) {
        RaceRoom raceRoom = findRaceRoom(roomCode);
        Set<QuestionOperation> operations = validateOperations(request);
        Set<QuestionNumberType> numberTypes = validateNumberTypes(request);
        QuestionDifficulty difficulty = validateDifficulty(request);

        RaceQuestionSettings settings = raceQuestionSettingsRepository.findByRaceRoom(raceRoom)
                .orElseGet(() -> new RaceQuestionSettings(raceRoom, operations, numberTypes, difficulty));
        settings.update(operations, numberTypes, difficulty);

        return QuestionSettingsResponse.fromEntity(raceQuestionSettingsRepository.save(settings));
    }

    @Transactional(readOnly = true)
    public RaceQuestionSettings getSettingsOrDefault(RaceRoom raceRoom) {
        return raceQuestionSettingsRepository.findByRaceRoom(raceRoom)
                .orElseGet(() -> new RaceQuestionSettings(
                        raceRoom,
                        DEFAULT_OPERATIONS,
                        DEFAULT_NUMBER_TYPES,
                        DEFAULT_DIFFICULTY
                ));
    }

    private RaceRoom findRaceRoom(String roomCode) {
        return raceRoomRepository.findByRoomCode(roomCode.toUpperCase())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Race room not found"));
    }

    private Set<QuestionOperation> validateOperations(UpdateQuestionSettingsRequest request) {
        if (request == null || request.getOperations() == null || request.getOperations().isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "At least one operation is required");
        }

        return EnumSet.copyOf(request.getOperations());
    }

    private Set<QuestionNumberType> validateNumberTypes(UpdateQuestionSettingsRequest request) {
        if (request == null || request.getNumberTypes() == null || request.getNumberTypes().isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "At least one number type is required");
        }

        Set<QuestionNumberType> numberTypes = EnumSet.copyOf(request.getNumberTypes());
        if (!numberTypes.equals(DEFAULT_NUMBER_TYPES)) {
            throw new ResponseStatusException(BAD_REQUEST, "Only whole numbers are supported right now");
        }

        return numberTypes;
    }

    private QuestionDifficulty validateDifficulty(UpdateQuestionSettingsRequest request) {
        if (request == null || request.getDifficulty() == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Difficulty is required");
        }

        if (request.getDifficulty() != DEFAULT_DIFFICULTY) {
            throw new ResponseStatusException(BAD_REQUEST, "Only easy difficulty is supported right now");
        }

        return request.getDifficulty();
    }
}
