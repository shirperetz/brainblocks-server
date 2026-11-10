package com.example.brainblocks.service;

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
import java.util.List;
import java.util.Set;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class RaceQuestionSettingsService {

    private final RaceRoomRepository raceRoomRepository;
    private final RaceQuestionSettingsRepository raceQuestionSettingsRepository;

    public RaceQuestionSettingsService(
            RaceRoomRepository raceRoomRepository,
            RaceQuestionSettingsRepository raceQuestionSettingsRepository
    ) {
        this.raceRoomRepository = raceRoomRepository;
        this.raceQuestionSettingsRepository = raceQuestionSettingsRepository;
    }

    @Transactional(readOnly = true)
    public RaceQuestionSettings getSettingsOrDefault(RaceRoom raceRoom) {
        return raceQuestionSettingsRepository.findByRaceRoom(raceRoom).orElseGet(() -> {
            RaceQuestionSettings s = new RaceQuestionSettings(
                    raceRoom,
                    EnumSet.of(QuestionOperation.ADDITION, QuestionOperation.SUBTRACTION),
                    EnumSet.of(QuestionNumberType.WHOLE_NUMBERS),
                    QuestionDifficulty.EASY,
                    30, false
            );
            return raceQuestionSettingsRepository.save(s);
        });
    }

    @Transactional
    public RaceQuestionSettings createOrUpdate(RaceRoom raceRoom, List<String> operations,
                                                List<String> numberTypes, String difficulty,
                                                int questionTimeLimit, boolean manualMode) {
        Set<QuestionOperation> ops = parseOperations(operations);
        Set<QuestionNumberType> types = parseNumberTypes(numberTypes);
        QuestionDifficulty diff = parseDifficulty(difficulty);
        int timeLimit = questionTimeLimit > 0 ? questionTimeLimit : 30;

        return raceQuestionSettingsRepository.findByRaceRoom(raceRoom).map(s -> {
            s.update(ops, types, diff, timeLimit, manualMode);
            return raceQuestionSettingsRepository.save(s);
        }).orElseGet(() -> raceQuestionSettingsRepository.save(
                new RaceQuestionSettings(raceRoom, ops, types, diff, timeLimit, manualMode)));
    }

    @Transactional
    public RaceQuestionSettings updateByRoomCode(String roomCode, List<String> operations,
                                                  List<String> numberTypes, String difficulty,
                                                  int questionTimeLimit, boolean manualMode) {
        RaceRoom raceRoom = raceRoomRepository.findByRoomCode(roomCode.toUpperCase())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Race room not found"));
        return createOrUpdate(raceRoom, operations, numberTypes, difficulty, questionTimeLimit, manualMode);
    }

    private Set<QuestionOperation> parseOperations(List<String> operations) {
        Set<QuestionOperation> ops = EnumSet.noneOf(QuestionOperation.class);
        if (operations != null) {
            for (String op : operations) {
                try { ops.add(QuestionOperation.valueOf(op.toUpperCase())); } catch (Exception ignored) {}
            }
        }
        if (ops.isEmpty()) ops.add(QuestionOperation.ADDITION);
        return ops;
    }

    private Set<QuestionNumberType> parseNumberTypes(List<String> numberTypes) {
        Set<QuestionNumberType> types = EnumSet.noneOf(QuestionNumberType.class);
        if (numberTypes != null) {
            for (String nt : numberTypes) {
                try { types.add(QuestionNumberType.valueOf(nt.toUpperCase())); } catch (Exception ignored) {}
            }
        }
        if (types.isEmpty()) types.add(QuestionNumberType.WHOLE_NUMBERS);
        return types;
    }

    private QuestionDifficulty parseDifficulty(String difficulty) {
        if (difficulty == null) return QuestionDifficulty.EASY;
        try { return QuestionDifficulty.valueOf(difficulty.toUpperCase()); } catch (Exception e) { return QuestionDifficulty.EASY; }
    }
}
