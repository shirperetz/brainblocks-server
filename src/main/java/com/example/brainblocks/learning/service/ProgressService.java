package com.example.brainblocks.learning.service;

import com.example.brainblocks.learning.dto.request.AnswerDto;
import com.example.brainblocks.learning.dto.request.SubmitSessionRequest;
import com.example.brainblocks.learning.dto.response.MapResponse;
import com.example.brainblocks.learning.dto.response.MistakeDto;
import com.example.brainblocks.learning.dto.response.SessionSummaryResponse;
import com.example.brainblocks.learning.dto.response.StageDto;
import com.example.brainblocks.learning.model.LearnerMistake;
import com.example.brainblocks.learning.model.LearnerProfile;
import com.example.brainblocks.learning.model.LearnerSession;
import com.example.brainblocks.learning.model.LearnerStageProgress;
import com.example.brainblocks.learning.model.SessionMode;
import com.example.brainblocks.learning.model.StageStatus;
import com.example.brainblocks.learning.model.Topic;
import com.example.brainblocks.learning.repository.LearnerMistakeRepository;
import com.example.brainblocks.learning.repository.LearnerProfileRepository;
import com.example.brainblocks.learning.repository.LearnerSessionRepository;
import com.example.brainblocks.learning.repository.LearnerStageProgressRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

/**
 * The progression brain of the learning game: builds a topic's stage map, and processes a
 * finished session — scoring, stars, mistakes, points, and unlocking the next stage.
 */
@Service
public class ProgressService {

    static final int STAGES_PER_TOPIC = 6;
    static final int PASS_PCT = 60;

    private final LearnerProfileService profileService;
    private final LearnerProfileRepository profileRepository;
    private final LearnerStageProgressRepository stageProgressRepository;
    private final LearnerSessionRepository sessionRepository;
    private final LearnerMistakeRepository mistakeRepository;

    public ProgressService(LearnerProfileService profileService,
                           LearnerProfileRepository profileRepository,
                           LearnerStageProgressRepository stageProgressRepository,
                           LearnerSessionRepository sessionRepository,
                           LearnerMistakeRepository mistakeRepository) {
        this.profileService = profileService;
        this.profileRepository = profileRepository;
        this.stageProgressRepository = stageProgressRepository;
        this.sessionRepository = sessionRepository;
        this.mistakeRepository = mistakeRepository;
    }

    /** Builds the 6-stage map for a topic. Stage 1 is always open; absent rows are locked. */
    public MapResponse getMap(Long profileId, Topic topic) {
        profileService.findProfile(profileId); // 404 if no such profile
        Map<Integer, LearnerStageProgress> byStage = new HashMap<>();
        for (LearnerStageProgress row : stageProgressRepository.findByProfileIdAndTopic(profileId, topic)) {
            byStage.put(row.getStageNumber(), row);
        }

        List<StageDto> stages = new ArrayList<>(STAGES_PER_TOPIC);
        int totalStars = 0;
        for (int stage = 1; stage <= STAGES_PER_TOPIC; stage++) {
            LearnerStageProgress row = byStage.get(stage);
            if (row != null) {
                stages.add(new StageDto(stage, row.getStatus().name(), row.getStarsEarned(), row.getBestScorePct()));
                totalStars += row.getStarsEarned();
            } else {
                StageStatus status = stage == 1 ? StageStatus.UNLOCKED : StageStatus.LOCKED;
                stages.add(new StageDto(stage, status.name(), 0, 0));
            }
        }
        return new MapResponse(topic.name(), stages, totalStars);
    }

    @Transactional
    public SessionSummaryResponse submitSession(SubmitSessionRequest request) {
        if (request.getProfileId() == null) {
            throw new ResponseStatusException(BAD_REQUEST, "profileId is required");
        }
        List<AnswerDto> answers = request.getAnswers();
        if (answers == null || answers.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "answers are required");
        }

        LearnerProfile profile = profileService.findProfile(request.getProfileId());
        Topic topic = parseTopic(request.getTopic());
        SessionMode mode = parseMode(request.getMode());
        int stage = Math.min(Math.max(request.getStage(), 1), STAGES_PER_TOPIC);

        int total = answers.size();
        int correct = (int) answers.stream().filter(AnswerDto::isCorrect).count();
        int hintsUsed = (int) answers.stream().filter(AnswerDto::isHintUsed).count();
        int scorePct = (int) Math.round(correct * 100.0 / total);
        int points = computePoints(answers);
        int stars = starsFor(scorePct);
        boolean passed = scorePct >= PASS_PCT;

        // Points + activity on the profile.
        profile.addPoints(points);
        profile.touch();
        profileRepository.save(profile);

        // Persist the session.
        sessionRepository.save(new LearnerSession(
                profile.getId(), topic, profile.getGrade(), stage, mode,
                total, correct, points, stars, hintsUsed, request.getDurationMs()));

        // Persist mistakes (wrong answers) for the review screen.
        List<MistakeDto> mistakeDtos = new ArrayList<>();
        for (AnswerDto a : answers) {
            if (!a.isCorrect()) {
                Topic mistakeTopic = a.getTopic() != null ? parseTopic(a.getTopic()) : topic;
                LearnerMistake saved = mistakeRepository.save(new LearnerMistake(
                        profile.getId(), mistakeTopic, a.getQuestionText(),
                        a.getChosenAnswer(), a.getCorrectAnswer(), a.getExplanation()));
                mistakeDtos.add(MistakeDto.fromEntity(saved));
            }
        }

        // Stage progress + unlock (only real stage play counts toward the map).
        boolean nextUnlocked = false;
        int nextStage = 0;
        if (mode == SessionMode.STAGE) {
            LearnerStageProgress current = upsertStage(profile.getId(), topic, stage, StageStatus.UNLOCKED);
            current.recordAttempt(scorePct, stars, passed);
            stageProgressRepository.save(current);

            if (passed && stage < STAGES_PER_TOPIC) {
                LearnerStageProgress next = upsertStage(profile.getId(), topic, stage + 1, StageStatus.LOCKED);
                next.unlock();
                stageProgressRepository.save(next);
                nextUnlocked = true;
                nextStage = stage + 1;
            }
        }

        return new SessionSummaryResponse(correct, total, scorePct, points, stars, passed,
                nextUnlocked, nextStage, profile.getTotalPoints(),
                encouragement(passed, stars), mistakeDtos, List.of());
    }

    private LearnerStageProgress upsertStage(Long profileId, Topic topic, int stage, StageStatus createAs) {
        return stageProgressRepository.findByProfileIdAndTopicAndStageNumber(profileId, topic, stage)
                .orElseGet(() -> new LearnerStageProgress(profileId, topic, stage, createAs));
    }

    /** +10 per correct answer; +5 extra once the child is on a streak of 3 or more. */
    private int computePoints(List<AnswerDto> answers) {
        int points = 0;
        int streak = 0;
        for (AnswerDto a : answers) {
            if (a.isCorrect()) {
                points += 10;
                streak++;
                if (streak >= 3) points += 5;
            } else {
                streak = 0;
            }
        }
        return points;
    }

    private int starsFor(int scorePct) {
        if (scorePct >= 90) return 3;
        if (scorePct >= 75) return 2;
        if (scorePct >= PASS_PCT) return 1;
        return 0;
    }

    private String encouragement(boolean passed, int stars) {
        if (passed && stars == 3) return "מדהים! ענית כמעט על הכל נכון!";
        if (passed) return "כל הכבוד! סיימת את השלב!";
        return "היית קרוב! נתרגל עוד קצת ונצליח";
    }

    private Topic parseTopic(String value) {
        if (value == null) throw new ResponseStatusException(BAD_REQUEST, "topic is required");
        try {
            return Topic.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid topic: " + value);
        }
    }

    private SessionMode parseMode(String value) {
        if (value == null) return SessionMode.STAGE;
        try {
            return SessionMode.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return SessionMode.STAGE;
        }
    }
}
