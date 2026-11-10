package com.example.brainblocks.learning.controller;

import com.example.brainblocks.learning.dto.request.CreateProfileRequest;
import com.example.brainblocks.learning.dto.request.SubmitSessionRequest;
import com.example.brainblocks.learning.dto.request.UpdateProfileRequest;
import com.example.brainblocks.learning.dto.response.MapResponse;
import com.example.brainblocks.learning.dto.response.ProfileResponse;
import com.example.brainblocks.learning.dto.response.QuestionResponse;
import com.example.brainblocks.learning.dto.response.SessionSummaryResponse;
import com.example.brainblocks.learning.model.LearnerProfile;
import com.example.brainblocks.learning.model.Topic;
import com.example.brainblocks.learning.service.LearnerProfileService;
import com.example.brainblocks.learning.service.LearningQuestionService;
import com.example.brainblocks.learning.service.ProgressService;
import org.springframework.http.HttpStatus;
import com.example.brainblocks.learning.ai.HelpService;
import com.example.brainblocks.learning.ai.SpeechService;
import com.example.brainblocks.learning.dto.request.HelpRequest;
import com.example.brainblocks.learning.dto.response.MistakeDto;
import com.example.brainblocks.learning.model.LearnerMistake;
import com.example.brainblocks.learning.model.LearnerSession;
import com.example.brainblocks.learning.model.SessionMode;
import com.example.brainblocks.learning.repository.LearnerMistakeRepository;
import com.example.brainblocks.learning.repository.LearnerSessionRepository;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

/**
 * REST entry point for the kids learning game ("עולם החשבון"). All endpoints live under
 * {@code /api/learn}. Kept separate from the race controllers so the two products never collide.
 */
@RestController
@RequestMapping("/api/learn")
public class LearnController {

    private final LearnerProfileService profileService;
    private final LearningQuestionService questionService;
    private final ProgressService progressService;
    private final HelpService helpService;
    private final SpeechService speechService;
    private final LearnerMistakeRepository mistakeRepository;
    private final LearnerSessionRepository sessionRepository;

    public LearnController(LearnerProfileService profileService,
                           LearningQuestionService questionService,
                           ProgressService progressService,
                           HelpService helpService,
                           SpeechService speechService,
                           LearnerMistakeRepository mistakeRepository,
                           LearnerSessionRepository sessionRepository) {
        this.profileService = profileService;
        this.questionService = questionService;
        this.progressService = progressService;
        this.helpService = helpService;
        this.speechService = speechService;
        this.mistakeRepository = mistakeRepository;
        this.sessionRepository = sessionRepository;
    }

    // ---- Profiles ----------------------------------------------------------

    @PostMapping("/profiles")
    @ResponseStatus(HttpStatus.CREATED)
    public ProfileResponse createProfile(@RequestBody CreateProfileRequest request) {
        return profileService.createProfile(request);
    }

    @GetMapping("/profiles/{id}")
    public ProfileResponse getProfile(@PathVariable Long id) {
        return profileService.getProfile(id);
    }

    @PatchMapping("/profiles/{id}")
    public ProfileResponse updateProfile(@PathVariable Long id,
                                         @RequestBody UpdateProfileRequest request) {
        return profileService.updateProfile(id, request);
    }

    // ---- Gameplay ----------------------------------------------------------

    /** Generates a batch of questions for a stage, scaled by the profile's grade + difficulty. */
    @GetMapping("/questions")
    public List<QuestionResponse> getQuestions(@RequestParam Long profileId,
                                               @RequestParam String topic,
                                               @RequestParam(defaultValue = "1") int stage,
                                               @RequestParam(defaultValue = "10") int count) {
        LearnerProfile profile = profileService.findProfile(profileId);
        int clamped = Math.min(Math.max(count, 1), 20);
        return questionService.generate(parseTopic(topic), profile.getGrade(),
                profile.getDifficulty(), stage, clamped);
    }

    @GetMapping("/profiles/{id}/map")
    public MapResponse getMap(@PathVariable Long id, @RequestParam String topic) {
        return progressService.getMap(id, parseTopic(topic));
    }

    @PostMapping("/sessions")
    public SessionSummaryResponse submitSession(@RequestBody SubmitSessionRequest request) {
        return progressService.submitSession(request);
    }

    // ---- Help ("לא הבנתי") -----------------------------------------------

    /** A short explanation of one question from the local AI, or the game's own hint. Never the answer. */
    @PostMapping("/help")
    public Map<String, String> help(@RequestBody HelpRequest r) {
        HelpService.Help h = helpService.explain(r.getQuestionText(), r.getCorrectAnswer(), r.getChosenAnswer(),
                r.getGrade(), r.isAgain(), r.getFallbackHint());
        return Map.of("text", h.text(), "source", h.source());
    }

    /** The help text read aloud (WAV) for children who can't read yet; 204 when no voice is available. */
    @PostMapping(path = "/speak", produces = "audio/wav")
    public ResponseEntity<byte[]> speak(@RequestBody Map<String, String> body) {
        byte[] wav = speechService.speak(body.get("text"));
        return wav == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(wav);
    }

    // ---- Review mistakes + challenges -------------------------------------

    /** Mistakes not fixed yet, newest first (at most 20). */
    @GetMapping("/profiles/{id}/mistakes")
    public List<MistakeDto> mistakes(@PathVariable Long id) {
        profileService.findProfile(id);
        return mistakeRepository.findByProfileIdAndResolvedFalseOrderByCreatedAtDesc(id).stream()
                .limit(20).map(MistakeDto::fromEntity).toList();
    }

    /** The child answered a past mistake correctly - it leaves the review list. */
    @PostMapping("/mistakes/{id}/resolve")
    public Map<String, Boolean> resolveMistake(@PathVariable Long id, @RequestParam Long profileId) {
        LearnerMistake m = mistakeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "mistake not found"));
        if (!m.getProfileId().equals(profileId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "mistake not found");
        m.resolve();
        mistakeRepository.save(m);
        return Map.of("resolved", true);
    }

    /** For the home screen: daily streak, whether today's challenge is done, best 60-second score, open mistakes. */
    @GetMapping("/profiles/{id}/stats")
    public Map<String, Object> stats(@PathVariable Long id) {
        LearnerProfile p = profileService.findProfile(id);
        List<LearnerSession> sessions = sessionRepository.findByProfileId(id);
        int bestTimed = sessions.stream().filter(s -> s.getMode() == SessionMode.TIMED)
                .mapToInt(LearnerSession::getCorrectCount).max().orElse(0);
        java.time.LocalDate today = java.time.LocalDate.now();
        boolean dailyDone = today.equals(p.getLastDailyDate());
        int streak = dailyDone || today.minusDays(1).equals(p.getLastDailyDate()) ? p.getDailyStreak() : 0;
        long open = mistakeRepository.findByProfileIdAndResolvedFalseOrderByCreatedAtDesc(id).size();
        return Map.of("dailyStreak", streak, "dailyDone", dailyDone, "bestTimed", bestTimed,
                "openMistakes", open, "totalPoints", p.getTotalPoints());
    }

    private Topic parseTopic(String value) {
        try {
            return Topic.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid topic: " + value);
        }
    }
}
