package com.example.brainblocks.service;

import com.example.brainblocks.dto.response.AnswerResponse;
import com.example.brainblocks.dto.response.GameEventResponse;
import com.example.brainblocks.dto.response.LeaderboardEntryResponse;
import com.example.brainblocks.dto.response.RaceQuestionResponse;
import com.example.brainblocks.dto.response.RaceResultsResponse;
import com.example.brainblocks.model.DecisionChoice;
import com.example.brainblocks.model.GameEvent;
import com.example.brainblocks.model.GameEventType;
import com.example.brainblocks.model.PlayerStatus;
import com.example.brainblocks.model.QuestionDifficulty;
import com.example.brainblocks.model.QuestionOperation;
import com.example.brainblocks.model.RacePlayer;
import com.example.brainblocks.model.RaceQuestion;
import com.example.brainblocks.model.RaceQuestionSettings;
import com.example.brainblocks.model.RaceResult;
import com.example.brainblocks.model.RaceRoom;
import com.example.brainblocks.model.RaceRoomStatus;
import com.example.brainblocks.repository.GameEventRepository;
import com.example.brainblocks.repository.RacePlayerRepository;
import com.example.brainblocks.repository.RaceQuestionRepository;
import com.example.brainblocks.repository.RaceResultRepository;
import com.example.brainblocks.repository.RaceRoomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class RaceGameService {

    private static final int DECISION_TRIGGER_EVERY = 5;
    private static final double LUCK_EVENT_CHANCE = 0.15;
    private static final int HIGHWAY_BONUS = 80;
    static final int HIGHWAY_PENALTY = -20;
    private static final int TURBO_BONUS = 50;
    private static final int BREAKDOWN_PENALTY = -15;

    private final RaceRoomRepository raceRoomRepository;
    private final RacePlayerRepository racePlayerRepository;
    private final RaceQuestionRepository raceQuestionRepository;
    private final GameEventRepository gameEventRepository;
    private final RaceResultRepository raceResultRepository;
    private final RaceQuestionSettingsService settingsService;
    private final SseService sseService;
    private final SecureRandom random = new SecureRandom();

    public RaceGameService(RaceRoomRepository raceRoomRepository, RacePlayerRepository racePlayerRepository,
                           RaceQuestionRepository raceQuestionRepository, GameEventRepository gameEventRepository,
                           RaceResultRepository raceResultRepository, RaceQuestionSettingsService settingsService,
                           SseService sseService) {
        this.raceRoomRepository = raceRoomRepository;
        this.racePlayerRepository = racePlayerRepository;
        this.raceQuestionRepository = raceQuestionRepository;
        this.gameEventRepository = gameEventRepository;
        this.raceResultRepository = raceResultRepository;
        this.settingsService = settingsService;
        this.sseService = sseService;
    }

    @Transactional
    public RaceQuestionResponse getOrGenerateQuestion(String roomCode, Long participantId) {
        RaceRoom room = findRoom(roomCode);
        validateInProgress(room);
        RacePlayer player = findApprovedPlayer(room, participantId);

        if (player.isPendingDecision()) {
            throw new ResponseStatusException(CONFLICT, "PENDING_DECISION");
        }

        Optional<RaceQuestion> existing = raceQuestionRepository.findByParticipantAndAnsweredFalse(player);
        if (existing.isPresent()) {
            return RaceQuestionResponse.fromEntity(existing.get());
        }

        RaceQuestionSettings settings = settingsService.getSettingsOrDefault(room);
        QuestionDifficulty difficulty = determineDifficulty(settings, player);
        RaceQuestion question = generateQuestion(room, player, difficulty, settings, false, false);
        player.setCurrentQuestionId(question.getId());
        racePlayerRepository.save(player);

        return RaceQuestionResponse.fromEntity(question);
    }

    @Transactional
    public AnswerResponse submitAnswer(String roomCode, Long participantId, Long questionId, Integer answer) {
        RaceRoom room = findRoom(roomCode);
        validateInProgress(room);
        RacePlayer player = findApprovedPlayer(room, participantId);
        RaceQuestion question = findQuestion(questionId, player);

        if (question.isAnswered()) {
            throw new ResponseStatusException(CONFLICT, "Question already answered");
        }

        long answerTimeMs = java.time.Duration.between(question.getCreatedAt(), LocalDateTime.now()).toMillis();
        question.markAnswered();
        raceQuestionRepository.save(question);

        boolean correct = answer != null && answer == question.getCorrectAnswer();
        int pointsEarned = 0;
        String luckEvent = null;
        String luckMessage = null;
        int luckPointsDelta = 0;

        if (correct) {
            pointsEarned = calculatePoints(question, answerTimeMs, settingsService.getSettingsOrDefault(room));
            int scoreBefore = player.getScore();
            player.addCorrectAnswer(answerTimeMs, pointsEarned);

            if (question.isDirtRoadQuestion()) {
                player.decrementDirtRoad();
            }

            if (player.getDirtRoadQuestionsLeft() == 0 && !question.isDirtRoadQuestion()) {
                checkDecisionTrigger(player);
            }

            if (!player.isPendingDecision()) {
                LuckResult luck = tryLuckEvent(room, player);
                if (luck != null) {
                    luckEvent = luck.type.name();
                    luckMessage = luck.message;
                    luckPointsDelta = luck.pointsDelta;
                }
            }

            broadcastOvertake(room, player, scoreBefore);
        } else {
            player.addWrongAnswer(answerTimeMs);
        }

        racePlayerRepository.save(player);
        broadcastPositionUpdate(room, player);
        broadcastLeaderboard(room);

        checkRaceFinish(room, player);

        return new AnswerResponse(
                question.getId(), player.getId(), answer != null ? answer : -1,
                correct, pointsEarned, player.getScore(), player.getPosition(),
                player.getCurrentStreak(), player.isPendingDecision(),
                luckEvent, luckMessage, luckPointsDelta
        );
    }

    @Transactional
    public RaceQuestionResponse handleDecision(String roomCode, Long participantId, String choice) {
        RaceRoom room = findRoom(roomCode);
        validateInProgress(room);
        RacePlayer player = findApprovedPlayer(room, participantId);

        if (!player.isPendingDecision()) {
            throw new ResponseStatusException(CONFLICT, "No pending decision for this player");
        }

        player.setPendingDecision(false);
        player.resetDecisionCounter();

        DecisionChoice decisionChoice;
        try {
            decisionChoice = DecisionChoice.valueOf(choice.toUpperCase());
        } catch (Exception e) {
            decisionChoice = DecisionChoice.DIRT_ROAD;
        }

        RaceQuestionSettings settings = settingsService.getSettingsOrDefault(room);
        RaceQuestion question;

        if (decisionChoice == DecisionChoice.HIGHWAY) {
            question = generateQuestion(room, player, QuestionDifficulty.HARD, settings, true, false);
            broadcastGameEvent(room, player, GameEventType.DECISION_HIGHWAY,
                    player.getDisplayName() + " chose the highway! Hard question incoming.", 0);
        } else {
            player.setDirtRoadQuestionsLeft(3);
            question = generateQuestion(room, player, QuestionDifficulty.EASY, settings, false, true);
            broadcastGameEvent(room, player, GameEventType.DECISION_DIRT_ROAD,
                    player.getDisplayName() + " chose the dirt road! 3 easier questions ahead.", 0);
        }

        player.setCurrentQuestionId(question.getId());
        racePlayerRepository.save(player);

        return RaceQuestionResponse.fromEntity(question);
    }

    @Transactional(readOnly = true)
    public List<LeaderboardEntryResponse> getLeaderboard(String roomCode) {
        RaceRoom room = findRoom(roomCode);
        List<RacePlayer> players = racePlayerRepository.findByRaceRoomAndStatusOrderByPositionDesc(room, PlayerStatus.APPROVED);
        List<LeaderboardEntryResponse> leaderboard = new ArrayList<>();
        for (int i = 0; i < players.size(); i++) {
            leaderboard.add(LeaderboardEntryResponse.fromPlayer(players.get(i), i + 1));
        }
        return leaderboard;
    }

    @Transactional
    public RaceResultsResponse finishRace(String roomCode) {
        RaceRoom room = findRoom(roomCode);
        if (room.getStatus() == RaceRoomStatus.FINISHED) {
            return buildResults(room);
        }
        room.finishRace();
        raceRoomRepository.save(room);
        saveResults(room);
        broadcastRaceFinished(room);
        return buildResults(room);
    }

    @Transactional(readOnly = true)
    public RaceResultsResponse getResults(String roomCode) {
        RaceRoom room = findRoom(roomCode);
        return buildResults(room);
    }

    @Transactional(readOnly = true)
    public List<GameEventResponse> getRecentEvents(String roomCode) {
        RaceRoom room = findRoom(roomCode);
        return gameEventRepository.findTop20ByRaceRoomOrderByCreatedAtDesc(room)
                .stream().map(GameEventResponse::fromEntity).toList();
    }

    private void checkDecisionTrigger(RacePlayer player) {
        if (player.getCorrectAnswersSinceDecision() >= DECISION_TRIGGER_EVERY) {
            player.setPendingDecision(true);
            broadcastDecisionEvent(player.getRaceRoom(), player);
        }
    }

    private void checkRaceFinish(RaceRoom room, RacePlayer player) {
        if (player.getPosition() >= room.getTrackLength()) {
            player.setFinishedAt(LocalDateTime.now());
            racePlayerRepository.save(player);
            finishRace(room.getRoomCode());
        }
    }

    private int calculatePoints(RaceQuestion question, long answerTimeMs, RaceQuestionSettings settings) {
        int base = switch (question.getDifficulty()) {
            case EASY -> 10;
            case MEDIUM -> 20;
            case HARD -> question.isDecisionQuestion() ? HIGHWAY_BONUS : 40;
        };

        int timeLimit = question.getTimeLimit() * 1000L > 0 ? question.getTimeLimit() * 1000 : 30000;
        double ratio = Math.max(0.0, 1.0 - (double) answerTimeMs / timeLimit);
        int speedBonus = (int) (base * 0.5 * ratio);

        return base + speedBonus;
    }

    private RaceQuestion generateQuestion(RaceRoom room, RacePlayer player, QuestionDifficulty difficulty,
                                          RaceQuestionSettings settings, boolean isDecisionQ, boolean isDirtRoad) {
        QuestionOperation operation = randomOperation(settings);
        int[] operands = generateOperands(operation, difficulty);
        int first = operands[0], second = operands[1];
        int correctAnswer = calculateAnswer(operation, first, second);
        String questionText = first + " " + symbol(operation) + " " + second + " = ?";
        int timeLimit = isDecisionQ ? 15 : (difficulty == QuestionDifficulty.HARD ? 20 : settings.getQuestionTimeLimit());

        RaceQuestion q = new RaceQuestion(room, player, questionText, correctAnswer, operation, difficulty, timeLimit, isDecisionQ, isDirtRoad);
        return raceQuestionRepository.save(q);
    }

    private int[] generateOperands(QuestionOperation operation, QuestionDifficulty difficulty) {
        return switch (difficulty) {
            case EASY -> generateEasyOperands(operation);
            case MEDIUM -> generateMediumOperands(operation);
            case HARD -> generateHardOperands(operation);
        };
    }

    private int[] generateEasyOperands(QuestionOperation op) {
        int a = random.nextInt(20) + 1, b = random.nextInt(20) + 1;
        if (op == QuestionOperation.SUBTRACTION && b > a) { int t = a; a = b; b = t; }
        if (op == QuestionOperation.DIVISION) { b = random.nextInt(10) + 1; a = b * (random.nextInt(10) + 1); }
        return new int[]{a, b};
    }

    private int[] generateMediumOperands(QuestionOperation op) {
        int a = random.nextInt(50) + 10, b = random.nextInt(50) + 10;
        if (op == QuestionOperation.SUBTRACTION && b > a) { int t = a; a = b; b = t; }
        if (op == QuestionOperation.MULTIPLICATION) { a = random.nextInt(12) + 2; b = random.nextInt(12) + 2; }
        if (op == QuestionOperation.DIVISION) { b = random.nextInt(12) + 2; a = b * (random.nextInt(12) + 2); }
        return new int[]{a, b};
    }

    private int[] generateHardOperands(QuestionOperation op) {
        int a = random.nextInt(200) + 50, b = random.nextInt(100) + 10;
        if (op == QuestionOperation.SUBTRACTION && b > a) { int t = a; a = b; b = t; }
        if (op == QuestionOperation.MULTIPLICATION) { a = random.nextInt(25) + 5; b = random.nextInt(25) + 5; }
        if (op == QuestionOperation.DIVISION) { b = random.nextInt(20) + 5; a = b * (random.nextInt(15) + 5); }
        return new int[]{a, b};
    }

    private int calculateAnswer(QuestionOperation op, int a, int b) {
        return switch (op) {
            case ADDITION -> a + b;
            case SUBTRACTION -> a - b;
            case MULTIPLICATION -> a * b;
            case DIVISION -> a / b;
        };
    }

    private String symbol(QuestionOperation op) {
        return switch (op) {
            case ADDITION -> "+";
            case SUBTRACTION -> "-";
            case MULTIPLICATION -> "×";
            case DIVISION -> "÷";
        };
    }

    private QuestionOperation randomOperation(RaceQuestionSettings settings) {
        QuestionOperation[] ops = settings.getOperations().toArray(new QuestionOperation[0]);
        return ops[random.nextInt(ops.length)];
    }

    private QuestionDifficulty determineDifficulty(RaceQuestionSettings settings, RacePlayer player) {
        if (player.getDirtRoadQuestionsLeft() > 0) return QuestionDifficulty.EASY;
        return settings.getDifficulty();
    }

    private LuckResult tryLuckEvent(RaceRoom room, RacePlayer player) {
        List<RacePlayer> all = racePlayerRepository.findByRaceRoomAndStatusOrderByPositionDesc(room, PlayerStatus.APPROVED);
        int rank = IntStream.range(0, all.size()).filter(i -> all.get(i).getId().equals(player.getId())).findFirst().orElse(0);
        boolean isBottom = rank >= all.size() / 2;
        boolean blockedByConsecutiveBadLuck = player.getConsecutiveBadLuck() >= 2;

        double chance = LUCK_EVENT_CHANCE;
        if (isBottom) chance *= 1.5;

        if (random.nextDouble() > chance) return null;

        int roll = random.nextInt(100);
        GameEventType type;
        int delta = 0;
        String message;

        if (roll < 25) {
            type = GameEventType.TURBO;
            delta = TURBO_BONUS;
            player.applyPointsDelta(delta);
            message = player.getDisplayName() + " got a TURBO boost! +" + delta + " points!";
        } else if (roll < 50 && !blockedByConsecutiveBadLuck && !player.isShieldActive()) {
            type = GameEventType.BREAKDOWN;
            delta = BREAKDOWN_PENALTY;
            player.applyPointsDelta(delta);
            player.recordBadLuck();
            message = player.getDisplayName() + " had a breakdown! " + delta + " points.";
        } else if (roll < 50 && player.isShieldActive()) {
            boolean consumed = player.consumeShield();
            if (consumed) {
                type = GameEventType.SHIELD;
                delta = 0;
                message = player.getDisplayName() + "'s shield blocked a breakdown!";
            } else {
                return null;
            }
        } else if (roll < 70) {
            type = GameEventType.SHIELD;
            player.activateShield();
            message = player.getDisplayName() + " got a shield! Protected from next breakdown.";
        } else if (roll < 90) {
            type = GameEventType.DOUBLE_FUEL;
            player.activateDoubleFuel();
            message = player.getDisplayName() + " activated Double Fuel! Next 3 correct answers are worth double!";
        } else {
            if (!isBottom) return null;
            type = GameEventType.QUESTION_SWAP;
            message = player.getDisplayName() + " got a Question Swap hint!";
        }

        GameEvent event = new GameEvent(room, player, type, message, delta);
        gameEventRepository.save(event);
        broadcastGameEvent(room, player, type, message, delta);

        return new LuckResult(type, message, delta);
    }

    private void broadcastPositionUpdate(RaceRoom room, RacePlayer player) {
        Map<String, Object> data = new HashMap<>();
        data.put("participantId", player.getId());
        data.put("displayName", player.getDisplayName());
        data.put("color", player.getColor());
        data.put("position", player.getPosition());
        data.put("score", player.getScore());
        data.put("currentStreak", player.getCurrentStreak());
        sseService.broadcast(room.getRoomCode(), "POSITION_UPDATED", data);
    }

    private void broadcastLeaderboard(RaceRoom room) {
        List<LeaderboardEntryResponse> leaderboard = getLeaderboard(room.getRoomCode());
        sseService.broadcast(room.getRoomCode(), "LEADERBOARD_UPDATED", leaderboard);
    }

    private void broadcastGameEvent(RaceRoom room, RacePlayer player, GameEventType type, String message, int delta) {
        Map<String, Object> data = new HashMap<>();
        data.put("participantId", player != null ? player.getId() : null);
        data.put("participantName", player != null ? player.getDisplayName() : "System");
        data.put("participantColor", player != null ? player.getColor() : "#ffffff");
        data.put("type", type.name());
        data.put("message", message);
        data.put("pointsDelta", delta);
        sseService.broadcast(room.getRoomCode(), "GAME_EVENT", data);
    }

    private void broadcastDecisionEvent(RaceRoom room, RacePlayer player) {
        Map<String, Object> data = new HashMap<>();
        data.put("participantId", player.getId());
        data.put("displayName", player.getDisplayName());
        data.put("message", "Choose your path: Highway (hard, high reward) or Dirt Road (easy, safe)");
        sseService.broadcast(room.getRoomCode(), "DECISION_EVENT", data);
    }

    private void broadcastOvertake(RaceRoom room, RacePlayer player, int scoreBefore) {
        List<RacePlayer> players = racePlayerRepository.findByRaceRoomAndStatusOrderByPositionDesc(room, PlayerStatus.APPROVED);
        int newRank = IntStream.range(0, players.size()).filter(i -> players.get(i).getId().equals(player.getId())).findFirst().orElse(-1);
        if (newRank > 0) {
            RacePlayer overtaken = players.get(newRank - 1);
            if (overtaken.getScore() < player.getScore() && scoreBefore < overtaken.getScore()) {
                Map<String, Object> data = new HashMap<>();
                data.put("participantId", player.getId());
                data.put("displayName", player.getDisplayName());
                data.put("overtakenId", overtaken.getId());
                data.put("overtakenName", overtaken.getDisplayName());
                sseService.broadcast(room.getRoomCode(), "OVERTAKE", data);
            }
        }
    }

    private void broadcastRaceFinished(RaceRoom room) {
        List<LeaderboardEntryResponse> leaderboard = getLeaderboard(room.getRoomCode());
        Map<String, Object> data = new HashMap<>();
        data.put("roomCode", room.getRoomCode());
        data.put("leaderboard", leaderboard);
        sseService.broadcast(room.getRoomCode(), "RACE_FINISHED", data);
    }

    private void saveResults(RaceRoom room) {
        List<RacePlayer> players = racePlayerRepository.findByRaceRoomAndStatusOrderByPositionDesc(room, PlayerStatus.APPROVED);
        for (int i = 0; i < players.size(); i++) {
            RaceResult result = new RaceResult(room, players.get(i), i + 1);
            raceResultRepository.save(result);
        }
    }

    private RaceResultsResponse buildResults(RaceRoom room) {
        List<RacePlayer> players = racePlayerRepository.findByRaceRoomAndStatusOrderByPositionDesc(room, PlayerStatus.APPROVED);
        List<RaceResultsResponse.ResultEntryResponse> entries = new ArrayList<>();
        for (int i = 0; i < players.size(); i++) {
            RacePlayer p = players.get(i);
            entries.add(new RaceResultsResponse.ResultEntryResponse(
                    p.getId(), p.getDisplayName(), p.getColor(), i + 1,
                    p.getScore(), p.getCorrectAnswers(), p.getWrongAnswers(),
                    p.getAccuracy(), p.getAverageAnswerTimeMs(), p.getBestStreak()
            ));
        }
        return new RaceResultsResponse(room.getRoomCode(), room.getName(), entries);
    }

    private RaceRoom findRoom(String roomCode) {
        return raceRoomRepository.findByRoomCode(roomCode.toUpperCase())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Race room not found"));
    }

    private void validateInProgress(RaceRoom room) {
        if (room.getStatus() != RaceRoomStatus.IN_PROGRESS) {
            throw new ResponseStatusException(CONFLICT, "Race is not in progress");
        }
    }

    private RacePlayer findApprovedPlayer(RaceRoom room, Long participantId) {
        if (participantId == null) throw new ResponseStatusException(BAD_REQUEST, "participantId is required");
        return racePlayerRepository.findByRaceRoomAndId(room, participantId)
                .filter(p -> p.getStatus() == PlayerStatus.APPROVED)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Approved participant not found"));
    }

    private RaceQuestion findQuestion(Long questionId, RacePlayer player) {
        if (questionId == null) throw new ResponseStatusException(BAD_REQUEST, "questionId is required");
        return raceQuestionRepository.findById(questionId)
                .filter(q -> q.getParticipant() != null && q.getParticipant().getId().equals(player.getId()))
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Question not found for this participant"));
    }

    private record LuckResult(GameEventType type, String message, int pointsDelta) {}
}
