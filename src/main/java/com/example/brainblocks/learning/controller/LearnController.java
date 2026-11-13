package com.example.brainblocks.learning.controller;

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
import org.springframework.web.bind.annotation.RequestAttribute;
import com.example.brainblocks.config.LearnerAuthConfig;
import com.example.brainblocks.learning.service.AccountService;
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
    private final AccountService accountService;

    public LearnController(LearnerProfileService profileService,
                           LearningQuestionService questionService,
                           ProgressService progressService,
                           HelpService helpService,
                           SpeechService speechService,
                           LearnerMistakeRepository mistakeRepository,
                           LearnerSessionRepository sessionRepository,
                          AccountService accountService) {
        this.accountService = accountService;
        this.profileService = profileService;
        this.questionService = questionService;
        this.progressService = progressService;
        this.helpService = helpService;
        this.speechService = speechService;
        this.mistakeRepository = mistakeRepository;
        this.sessionRepository = sessionRepository;
    }

    /** 2026-11-12: the child's recent answers set the difficulty per topic (AdaptiveDifficulty). */
    private com.example.brainblocks.learning.repository.LearnerAnswerRepository answerRepository;

    @org.springframework.beans.factory.annotation.Autowired
    void setAnswerRepository(com.example.brainblocks.learning.repository.LearnerAnswerRepository answers) {
        this.answerRepository = answers;
    }

    // ---- Profiles ----------------------------------------------------------

    // 2026-11-11: children's accounts - a profile is created only with a username + password
    // (POST /auth/register); every other call carries the device token (LearnerAuthConfig) and may
    // touch only that child's own profile (own()).

    @PostMapping("/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AccountService.Session register(@RequestBody Map<String, String> body) {
        return accountService.register(body.get("joinCode"), body.get("username"), body.get("password"), body.get("name"),
                body.get("avatar"), body.get("grade"), body.get("difficulty"));
    }

    /** 2026-11-12: "מורה" at sign-up - no code, waits for Shir's approval. body: { username, password, name } */
    @PostMapping("/auth/register-teacher")
    @ResponseStatus(HttpStatus.CREATED)
    public AccountService.Session registerTeacher(@RequestBody Map<String, String> body) {
        return accountService.registerTeacher(body.get("username"), body.get("password"), body.get("name"));
    }

    @PostMapping("/auth/login")
    public AccountService.Session login(@RequestBody Map<String, String> body) {
        return accountService.login(body.get("username"), body.get("password"), java.time.Instant.now());
    }

    @GetMapping("/profiles/{id}")
    public ProfileResponse getProfile(@PathVariable Long id, @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        own(id, me);
        return profileService.getProfile(id);
    }

    @PatchMapping("/profiles/{id}")
    public ProfileResponse updateProfile(@PathVariable Long id,
                                         @RequestBody UpdateProfileRequest request,
                                         @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        own(id, me);
        return profileService.updateProfile(id, request);
    }

    /**
     * 2026-11-11: "still here" - the app sends it (at most once a minute) while the child taps, so a
     * level played without any other request doesn't count as "not used" for the 30-minute log-out.
     * The token check itself (LearnerAuthConfig) records the use; nothing else happens.
     */
    @GetMapping("/ping")
    public org.springframework.http.ResponseEntity<Void> ping() {
        return org.springframework.http.ResponseEntity.noContent().build();
    }

    /** 2026-11-11 (settings): body { current, password } - a new token comes back for this device. */
    @PostMapping("/profiles/{id}/password")
    public AccountService.Session changePassword(@PathVariable Long id, @RequestBody Map<String, String> body,
                                                 @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        own(id, me);
        return accountService.changeOwnPassword(id, body.get("current"), body.get("password"));
    }

    /** Another child's profile looks like it doesn't exist. */
    private static void own(Long id, Long me) {
        if (id == null || !id.equals(me)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found");
        }
    }

    // ---- Gameplay ----------------------------------------------------------

    /** Generates a batch of questions for a stage, scaled by the profile's grade + difficulty. */
    @org.springframework.beans.factory.annotation.Autowired
    private com.example.brainblocks.learning.repository.LearnQuestionRepository bank;

    @GetMapping("/questions")
    public List<QuestionResponse> getQuestions(@RequestParam Long profileId,
                                               @RequestParam String topic,
                                               @RequestParam(defaultValue = "1") int stage,
                                               @RequestParam(defaultValue = "10") int count,
                                               @RequestParam(defaultValue = "false") boolean secure,
                                               @RequestParam(defaultValue = "0") int tier,
                                               @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        own(profileId, me);
        stage = Math.min(Math.max(stage, 1), 6);
        LearnerProfile profile = profileService.findProfile(profileId);
        int clamped = Math.min(Math.max(count, 1), 20);
        Topic t = parseTopic(topic);
        // 2026-11-12: a child locked to a grade practices only that grade's topics.
        if (profile.isGradeLocked() && !com.example.brainblocks.learning.service.GradeTopics.allowed(t, profile.getGrade())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "הנושא הזה לא שייך לכיתה שלך");
        }
        // 2026-11-12 (idea #3): one level harder after >=90% right in this topic, easier after <=50%.
        var history = answerRepository.findByProfileId(profileId);
        var difficulty = com.example.brainblocks.learning.service.AdaptiveDifficulty.forTopic(profile.getDifficulty(), t, history);
        // 2026-11-13 (endless islands): a generated island asks the hardest level of the child's own grade
        // (the generator never leaves the grade's number range - harder, not another grade's material).
        if (tier > 0) {
            difficulty = com.example.brainblocks.learning.model.Difficulty.HARD;
            stage = Math.min(6, stage + Math.min(tier, 20) - 1); // each island further on starts a level higher
        }
        // 2026-11-12 (Shir: "always different exercises"): not the ones answered right lately in this topic.
        java.util.Set<String> recentRight = history.stream()
                .filter(a -> a.isCorrect() && a.getTopic() == t && a.getQuestionText() != null)
                .sorted(java.util.Comparator.comparing(com.example.brainblocks.learning.model.LearnerAnswer::getCreatedAt,
                        java.util.Comparator.nullsFirst(java.util.Comparator.naturalOrder())).reversed())
                .limit(200)
                .map(a -> com.example.brainblocks.learning.service.LearningQuestionService.key(a.getQuestionText(), a.getCorrectAnswer()))
                .collect(java.util.stream.Collectors.toSet());
        List<QuestionResponse> fresh = questionService.generateFresh(t, profile.getGrade(), difficulty, stage, clamped, recentRight);
        // 2026-11-12 (Shir: "the exercises are saved in a bank, new ones every time"): every question
        // served is kept, so the server can check the answer itself and the history is complete.
        // secure=true (the new screens): the right answer is not sent - it's checked by /answer.
        String grade = profile.getGrade() == null ? null : profile.getGrade().name();
        List<QuestionResponse> out = new java.util.ArrayList<>(fresh.size());
        for (QuestionResponse q : fresh) {
            var row = bank.save(new com.example.brainblocks.learning.model.LearnQuestion(profileId,
                    (q.getTopic() != null ? q.getTopic() : t).name(), stage, grade, q.getText(), q.getOptions(),
                    q.getOptions().get(q.getCorrectIndex()), q.getSkill(), q.getExplanation()));
            q.withBankId(row.getId());
            out.add(secure ? q.withoutAnswer() : q);
        }
        return out;
    }

    /**
     * 2026-11-12: answer one bank question. The server checks it (first answer recorded, a retry is
     * checked too but doesn't overwrite) and only now reveals the right answer and the explanation.
     * body { answer: "the option text" }.
     */
    @PostMapping("/questions/{bankId}/answer")
    public Map<String, Object> answerQuestion(@PathVariable Long bankId, @RequestBody Map<String, String> body,
                                              @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        var q = bank.findById(bankId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "אין שאלה כזאת"));
        own(q.getProfileId(), me);
        boolean right = q.answer(body.get("answer"));
        bank.save(q);
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("correct", right);
        out.put("correctAnswer", q.getCorrectAnswer());
        out.put("correctIndex", q.optionList().indexOf(q.getCorrectAnswer()));
        out.put("explanation", q.getExplanation() == null ? "" : q.getExplanation());
        return out;
    }

    @GetMapping("/profiles/{id}/map")
    public MapResponse getMap(@PathVariable Long id, @RequestParam String topic,
                              @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        own(id, me);
        return progressService.getMap(id, parseTopic(topic));
    }

    @PostMapping("/sessions")
    public SessionSummaryResponse submitSession(@RequestBody SubmitSessionRequest request,
                                                @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        own(request.getProfileId(), me);
        LearnerProfile p = profileService.findProfile(request.getProfileId());
        if (p.isGradeLocked() && request.getTopic() != null
                && !com.example.brainblocks.learning.service.GradeTopics.allowed(parseTopic(request.getTopic()), p.getGrade())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "הנושא הזה לא שייך לכיתה שלך");
        }
        return progressService.submitSession(request);
    }

    // ---- Help ("לא הבנתי") -----------------------------------------------

    /** A short explanation of one question from the local AI, or the game's own hint. Never the answer. */
    @PostMapping("/help")
    public Map<String, String> help(@RequestBody HelpRequest r,
                                    @RequestAttribute(value = LearnerAuthConfig.ATTRIBUTE, required = false) Long me) {
        // 2026-11-12: the phone no longer has the right answer - take it from the bank (own questions only).
        if (r.getBankId() != null && me != null) {
            bank.findById(r.getBankId()).filter(q -> q.getProfileId().equals(me)).ifPresent(q -> {
                r.setCorrectAnswer(q.getCorrectAnswer());
                if (r.getQuestionText() == null) r.setQuestionText(q.getText());
            });
        }
        int attempt = r.getAttempt() != null ? r.getAttempt() : r.isAgain() ? 1 : 0;
        HelpService.Help h = helpService.explain(r.getQuestionText(), r.getCorrectAnswer(), r.getChosenAnswer(),
                r.getGrade(), attempt, r.getFallbackHint());
        return Map.of("text", h.text(), "source", h.source());
    }

    /** The help text read aloud (WAV) for children who can't read yet; 204 when no voice is available. */
    @PostMapping(path = "/speak", produces = "audio/wav")
    public ResponseEntity<byte[]> speak(@RequestBody Map<String, String> body) {
        byte[] wav = speechService.speak(body.get("text"));
        return wav == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(wav);
    }

    // ---- Review mistakes + challenges -------------------------------------

    /** Mistakes due for review today (2026-11-12: a day / 3 days / a week after a right answer), newest first (at most 20). */
    @GetMapping("/profiles/{id}/mistakes")
    public List<MistakeDto> mistakes(@PathVariable Long id, @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        own(id, me);
        profileService.findProfile(id);
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        return mistakeRepository.findByProfileIdAndResolvedFalseOrderByCreatedAtDesc(id).stream()
                .filter(m -> m.isDue(now)).limit(20).map(MistakeDto::fromEntity).toList();
    }

    /** Right in the review: back after 1, 3, 7 days, then learned (LearnerMistake.reviewedRight). */
    @PostMapping("/mistakes/{id}/resolve")
    public Map<String, Object> resolveMistake(@PathVariable Long id, @RequestParam Long profileId,
                                              @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        LearnerMistake m = ownMistake(id, profileId, me);
        m.reviewedRight(java.time.LocalDateTime.now());
        mistakeRepository.save(m);
        return reviewResult(m);
    }

    /** 2026-11-12: wrong in the review - back tomorrow, from the start. */
    @PostMapping("/mistakes/{id}/missed")
    public Map<String, Object> missedMistake(@PathVariable Long id, @RequestParam Long profileId,
                                             @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        LearnerMistake m = ownMistake(id, profileId, me);
        m.reviewedWrong(java.time.LocalDateTime.now());
        mistakeRepository.save(m);
        return reviewResult(m);
    }

    private LearnerMistake ownMistake(Long id, Long profileId, Long me) {
        own(profileId, me);
        LearnerMistake m = mistakeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "mistake not found"));
        if (!m.getProfileId().equals(profileId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "mistake not found");
        return m;
    }

    private static Map<String, Object> reviewResult(LearnerMistake m) {
        Map<String, Object> r = new java.util.LinkedHashMap<>();
        r.put("resolved", m.isResolved());
        r.put("nextReviewAt", m.getNextReviewAt());
        r.put("step", m.getReviewStep());
        return r;
    }

    /** For the home screen: daily streak, whether today's challenge is done, best 60-second score, open mistakes. */
    @GetMapping("/profiles/{id}/stats")
    public Map<String, Object> stats(@PathVariable Long id, @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        own(id, me);
        LearnerProfile p = profileService.findProfile(id);
        List<LearnerSession> sessions = sessionRepository.findByProfileId(id);
        int bestTimed = sessions.stream().filter(s -> s.getMode() == SessionMode.TIMED)
                .mapToInt(LearnerSession::getCorrectCount).max().orElse(0);
        java.time.LocalDate today = java.time.LocalDate.now();
        boolean dailyDone = today.equals(p.getLastDailyDate());
        int streak = dailyDone || today.minusDays(1).equals(p.getLastDailyDate()) ? p.getDailyStreak() : 0;
        java.time.LocalDateTime nowT = java.time.LocalDateTime.now();
        long open = mistakeRepository.findByProfileIdAndResolvedFalseOrderByCreatedAtDesc(id).stream()
                .filter(m -> m.isDue(nowT)).count(); // 2026-11-12: due for review today
        return Map.of("dailyStreak", streak, "dailyDone", dailyDone, "bestTimed", bestTimed,
                "openMistakes", open, "totalPoints", p.getTotalPoints());
    }

    /** 2026-11-12 (idea #5): the weekly tasks the child's teacher set for its class, with its progress. */
    private com.example.brainblocks.learning.service.AssignmentService assignmentService;

    @org.springframework.beans.factory.annotation.Autowired
    void setAssignmentService(com.example.brainblocks.learning.service.AssignmentService s) {
        this.assignmentService = s;
    }

    @GetMapping("/profiles/{id}/assignments")
    public List<Map<String, Object>> assignments(@PathVariable Long id, @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        own(id, me);
        return assignmentService.forChild(profileService.findProfile(id));
    }

    /** 2026-11-12 (idea #6): the building the child's class builds this week ({} without a class). */
    private com.example.brainblocks.learning.service.ClassBuildingService classBuilding;

    @org.springframework.beans.factory.annotation.Autowired
    void setClassBuilding(com.example.brainblocks.learning.service.ClassBuildingService s) {
        this.classBuilding = s;
    }

    @GetMapping("/profiles/{id}/class-building")
    public Map<String, Object> classBuilding(@PathVariable Long id, @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        own(id, me);
        Map<String, Object> b = classBuilding.forChild(profileService.findProfile(id));
        return b == null ? Map.of() : b;
    }

    private Topic parseTopic(String value) {
        try {
            return Topic.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid topic: " + value);
        }
    }
}
