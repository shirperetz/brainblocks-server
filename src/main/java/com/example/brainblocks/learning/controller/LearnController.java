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
import org.springframework.web.bind.annotation.RequestHeader;
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
    public Object register(@RequestBody Map<String, String> body) {
        botCheck(body);
        consentCheck(body, "parentConsent", "צריך שהורה יאשר את ההרשמה - מסמנים את התיבה למטה");
        AccountService.Session s = accountService.register(body.get("joinCode"), body.get("username"), body.get("password"), body.get("name"),
                body.get("avatar"), body.get("grade"), body.get("difficulty"));
        accountService.markConsent(s.profile().getId(), "yes".equals(body.get("parentConsent")));
        return secondStep(s);
    }

    /** 2026-11-12: "מורה" at sign-up - no code, waits for Shir's approval. body: { username, password, name } */
    @PostMapping("/auth/register-teacher")
    @ResponseStatus(HttpStatus.CREATED)
    public Object registerTeacher(@RequestBody Map<String, String> body) {
        botCheck(body);
        consentCheck(body, "privacyAccepted", "צריך לאשר את מדיניות הפרטיות - מסמנים את התיבה למטה");
        AccountService.Session s = accountService.registerTeacher(body.get("username"), body.get("password"), body.get("name"));
        accountService.markConsent(s.profile().getId(), "yes".equals(body.get("privacyAccepted")));
        return secondStep(s);
    }

    /**
     * 2026-11-13 (Shir: "protection against bots that sign up by themselves in a loop"): the sign-up form
     * has a hidden field ("website") a person never sees - a bot fills it; and the form reports how long
     * it was open ("formMs") - a person needs more than 3 seconds. (An old page without formMs passes.)
     */
    private static com.example.brainblocks.learning.service.PhoneAlerts botAlerts;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setBotAlerts(com.example.brainblocks.learning.service.PhoneAlerts a) {
        botAlerts = a;
    }

    /**
     * 2026-11-14 (privacy, Shir: items 1-4 before going public): the sign-up form sends the parent's
     * consent (a child) / the privacy policy (a teacher) with formMs - a form that sends formMs without it
     * is refused. (Like botCheck: a request without formMs - an old page - passes.)
     */
    static void consentCheck(Map<String, String> body, String field, String message) {
        if (body.get("formMs") != null && !"yes".equals(body.get(field))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
    }

    static void botCheck(Map<String, String> body) {
        String trap = body.get("website");
        if (trap != null && !trap.isBlank()) {
            org.slf4j.LoggerFactory.getLogger("brainblocks.signup").warn("sign-up refused: bot trap filled");
            if (botAlerts != null) botAlerts.send("bot-trap", "בוט ניסה להירשם לאתר ונחסם");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ההרשמה לא הצליחה");
        }
        String ms = body.get("formMs");
        if (ms != null) {
            try {
                if (Long.parseLong(ms.trim()) < 3000) {
                    org.slf4j.LoggerFactory.getLogger("brainblocks.signup").warn("sign-up refused: form filled in {} ms", ms);
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "רגע, לאט לאט 🙂 נסו שוב");
                }
            } catch (NumberFormatException ignored) {
                // not a number - ignore
            }
        }
    }

    @PostMapping("/auth/logout")
    public Map<String, Object> logout(@RequestHeader(value = "X-Learner-Token", required = false) String token) {
        accountService.logout(token);
        return Map.of("loggedOut", true);
    }

    @PostMapping("/auth/login")
    public Object login(@RequestBody Map<String, String> body) {
        return secondStep(accountService.login(body.get("username"), body.get("password"), java.time.Instant.now()));
    }

    private com.example.brainblocks.learning.service.AdminSecondFactor second;

    @org.springframework.beans.factory.annotation.Autowired
    void setSecond(com.example.brainblocks.learning.service.AdminSecondFactor second) {
        this.second = second;
    }

    /**
     * 2026-11-14 (public site): the admin gets her token only after the second step (fingerprint or a
     * backup code) - also when an admin username is registered (a stranger who signs up as "shir" on an
     * empty site gets nothing).
     */
    private Object secondStep(AccountService.Session s) {
        return second != null && second.required(s) ? second.begin(s) : s;
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
                                               @RequestParam(required = false) String foundation,
                                               @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        own(profileId, me);
        stage = Math.min(Math.max(stage, 1), 6);
        LearnerProfile profile = profileService.findProfile(profileId);
        int clamped = Math.min(Math.max(count, 1), 20);
        Topic t = parseTopic(topic);
        // 2026-11-21: the grade this topic is made at - her own, or a higher one her teacher approved.
        var grade0 = com.example.brainblocks.learning.service.AdvancedLevels.effective(profile, t);
        // 2026-11-21 (finding the gap): a foundations round - the base's own topic, range and level
        // (always at or under the child's grade), tagged with the base's skill.
        var base = foundation == null ? null : com.example.brainblocks.learning.service.Foundations.byKey(foundation);
        if (foundation != null && base == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "אין יסוד כזה");
        if (base != null) {
            t = base.topic();
            stage = base.stage();
            tier = 0;
            grade0 = profile.getGrade() != null && profile.getGrade().ordinal() < base.grade().ordinal() ? profile.getGrade() : base.grade();
        }
        // 2026-11-12: a child locked to a grade practices only that grade's topics.
        if (profile.isGradeLocked() && !com.example.brainblocks.learning.service.GradeTopics.allowed(t, grade0)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "הנושא הזה לא שייך לכיתה שלך");
        }
        // 2026-11-12 (idea #3): one level harder after >=90% right in this topic, easier after <=50%.
        // 2026-11-15 (speed): this topic's latest 400 only - every answer the child ever gave grew with time.
        final Topic topicNow = t;
        var history = answerRepository.findTop400ByProfileIdAndTopicOrderByIdDesc(profileId, t);
        var difficulty = com.example.brainblocks.learning.service.AdaptiveDifficulty.forTopic(profile.getDifficulty(), t, history);
        // 2026-11-13 (endless islands): a generated island asks the hardest level of the child's own grade
        // (the generator never leaves the grade's number range - harder, not another grade's material).
        if (tier > 0) {
            difficulty = com.example.brainblocks.learning.model.Difficulty.HARD;
            stage = Math.min(6, stage + Math.min(tier, 20) - 1); // each island further on starts a level higher
        }
        // 2026-11-12 (Shir: "always different exercises"): not the ones answered right lately in this topic.
        java.util.Set<String> recentRight = history.stream()
                .filter(a -> a.isCorrect() && a.getTopic() == topicNow && a.getQuestionText() != null)
                .sorted(java.util.Comparator.comparing(com.example.brainblocks.learning.model.LearnerAnswer::getCreatedAt,
                        java.util.Comparator.nullsFirst(java.util.Comparator.naturalOrder())).reversed())
                .limit(200)
                .map(a -> com.example.brainblocks.learning.service.LearningQuestionService.key(a.getQuestionText(), a.getCorrectAnswer()))
                .collect(java.util.stream.Collectors.toSet());
        if (base != null) difficulty = com.example.brainblocks.learning.model.Difficulty.MEDIUM;
        List<QuestionResponse> fresh = questionService.generateFresh(t, grade0, difficulty, stage, clamped, recentRight);
        if (base != null) fresh.forEach(q -> q.withSkill(base.skill()));
        // 2026-11-21 (calm mode): two options while this topic is still hard for her (not the 60 seconds)
        if (profile.isCalm() && secure && com.example.brainblocks.learning.service.CalmMode.twoOptions(history)) {
            java.util.Random r = new java.util.Random();
            fresh = new java.util.ArrayList<>(fresh.stream().map(q -> com.example.brainblocks.learning.service.CalmMode.twoOf(q, r)).toList());
        }
        // 2026-11-12 (Shir: "the exercises are saved in a bank, new ones every time"): every question
        // served is kept, so the server can check the answer itself and the history is complete.
        // secure=true (the new screens): the right answer is not sent - it's checked by /answer.
        String grade = grade0 == null ? null : grade0.name();
        // 2026-11-15 (speed): saved together in ONE transaction - one commit to the cloud database, not ten.
        List<com.example.brainblocks.learning.model.LearnQuestion> rows = new java.util.ArrayList<>(fresh.size());
        for (QuestionResponse q : fresh) {
            rows.add(new com.example.brainblocks.learning.model.LearnQuestion(profileId,
                    (q.getTopic() != null ? q.getTopic() : t).name(), stage, grade, q.getText(), q.getOptions(),
                    q.getOptions().get(q.getCorrectIndex()), q.getSkill(), q.getExplanation()));
        }
        if (!secure) rows.forEach(r -> r.setRevealed(true)); // the phone saw the answer
        var saved = bank.saveAll(rows);
        List<QuestionResponse> out = new java.util.ArrayList<>(fresh.size());
        for (int i = 0; i < fresh.size(); i++) {
            QuestionResponse q = fresh.get(i);
            q.withBankId(saved.get(i).getId());
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
        // 2026-11-21: a question that went out with its answer isn't judged here (a script could use it)
        if (q.isRevealed()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "השאלה הזאת לא נבדקת כאן");
        boolean right = q.answer(body.get("answer"));
        bank.save(q);
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("correct", right);
        out.put("correctAnswer", q.getCorrectAnswer());
        out.put("correctIndex", q.optionList().indexOf(q.getCorrectAnswer()));
        out.put("explanation", q.getExplanation() == null ? "" : q.getExplanation());
        return out;
    }

    /**
     * 2026-11-21 (finding the gap): the base skill to build now, or {} - from the child's own judged
     * answers (Foundations). { key, name, emoji, status: CHECK|PRACTICE, forTopic, topicPct, pct }.
     */
    @GetMapping("/profiles/{id}/foundation")
    public Map<String, Object> foundation(@PathVariable Long id, @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        own(id, me);
        LearnerProfile p = profileService.findProfile(id);
        var gap = com.example.brainblocks.learning.service.Foundations.find(answerRepository.findTop600ByProfileIdOrderByIdDesc(id), p.getGrade());
        return gap == null ? Map.of() : gap;
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
                && !com.example.brainblocks.learning.service.GradeTopics.allowed(parseTopic(request.getTopic()),
                        com.example.brainblocks.learning.service.AdvancedLevels.effective(p, parseTopic(request.getTopic())))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "הנושא הזה לא שייך לכיתה שלך");
        }
        return com.example.brainblocks.learning.service.RoundOnce.run(request.getProfileId(), request.getRoundKey(),
                () -> progressService.submitSession(request));
    }

    // ---- Help ("לא הבנתי") -----------------------------------------------

    /** A short explanation of one question from the local AI, or the game's own hint. Never the answer. */
    @PostMapping("/help")
    public Map<String, Object> help(@RequestBody HelpRequest r,
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
        // 2026-11-13 (Shir: explanations without tokens): first press after a wrong answer - a sentence
        // about THIS mistake; and a drawing of the question (both from code, free, never the answer).
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        String text = h.text();
        if (attempt == 0) {
            String m = com.example.brainblocks.learning.ai.MistakeHint.forMistake(r.getQuestionText(), r.getCorrectAnswer(), r.getChosenAnswer());
            if (m != null) text = "💡 " + m + "\n" + text;
        }
        out.put("text", text);
        out.put("source", h.source());
        Map<String, Object> visual = com.example.brainblocks.learning.ai.MistakeHint.visual(r.getQuestionText());
        if (visual != null) out.put("visual", visual);
        return out;
    }

    /** The help text read aloud (WAV) for children who can't read yet; 204 when no voice is available. */
    @PostMapping(path = "/speak", produces = "audio/wav")
    public ResponseEntity<byte[]> speak(@RequestBody Map<String, String> body) {
        byte[] wav = speechService.speak(body.get("text"));
        if (wav != null) return ResponseEntity.ok(wav);
        // 2026-11-14 (the public site has no voice of its own): the words to say, so the browser's own
        // voice can read them - with the math already in words ("38 + 27" -> "שלושים ושמונה ועוד...").
        String t = body.get("text");
        if (t == null || t.isBlank()) return ResponseEntity.noContent().build();
        String spoken = com.example.brainblocks.learning.service.SpokenMath.toSpeech(t);
        if (spoken.length() > 400) spoken = spoken.substring(0, 400);
        return ResponseEntity.noContent()
                .header("X-Spoken", java.net.URLEncoder.encode(spoken, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20"))
                .build();
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

    /** 2026-11-27 (idea #5 - mistakes island): ALL the child's open mistakes (not only the ones due today),
     *  so the island shows every spot to clean. Capped. */
    @GetMapping("/profiles/{id}/mistakes/all")
    public List<MistakeDto> allMistakes(@PathVariable Long id, @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        own(id, me);
        profileService.findProfile(id);
        return mistakeRepository.findByProfileIdAndResolvedFalseOrderByCreatedAtDesc(id).stream()
                .limit(30).map(MistakeDto::fromEntity).toList();
    }

    /** 2026-11-27 (idea #5): answered right on the mistakes island -> that spot is cleaned (fully resolved). */
    @PostMapping("/mistakes/{id}/clean")
    public Map<String, Object> cleanMistake(@PathVariable Long id, @RequestParam Long profileId,
                                            @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        LearnerMistake m = ownMistake(id, profileId, me);
        m.resolve();
        mistakeRepository.save(m);
        return Map.of("resolved", true);
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

    /** 2026-11-14 (A5): body { coins } - from the child's island to its class's building this week. */
    @PostMapping("/profiles/{id}/class-building/donate")
    public Map<String, Object> donateToBuilding(@PathVariable Long id, @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me,
                                                @RequestBody Map<String, Object> body) {
        own(id, me);
        Object c = body.get("coins");
        int coins = c instanceof Number n ? n.intValue() : 0;
        return classBuilding.donate(profileService.findProfile(id), coins);
    }

    private Topic parseTopic(String value) {
        try {
            return Topic.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid topic: " + value);
        }
    }
}
