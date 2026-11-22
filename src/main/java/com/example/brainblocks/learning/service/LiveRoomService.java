package com.example.brainblocks.learning.service;

import com.example.brainblocks.learning.dto.response.QuestionResponse;
import com.example.brainblocks.learning.model.Difficulty;
import com.example.brainblocks.learning.model.Grade;
import com.example.brainblocks.learning.model.LearnClass;
import com.example.brainblocks.learning.model.LearnerAnswer;
import com.example.brainblocks.learning.model.LearnerProfile;
import com.example.brainblocks.learning.model.SessionMode;
import com.example.brainblocks.learning.model.TeacherQuestion;
import com.example.brainblocks.learning.model.Topic;
import com.example.brainblocks.learning.repository.LearnClassRepository;
import com.example.brainblocks.learning.repository.LearnerAnswerRepository;
import com.example.brainblocks.learning.repository.LearnerProfileRepository;
import com.example.brainblocks.learning.repository.TeacherQuestionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * 2026-11-12 (Shir: "the teacher opens a room, the class comes in - also children who want to join, she
 * approves them; a math game; she sees live who leads; she picks the topics and can add her own
 * exercises"). A room lives in memory (a lesson, up to a few hours); what is kept is each logged-in
 * child's answers (learn_answer - reports, skills, the class building) and the teacher's own exercises
 * (teacher_question). The right answer never leaves the server before the child answers.
 */
@Service
public class LiveRoomService {

    public enum State { LOBBY, RUNNING, ENDED }

    static final String MY = "MY"; // "התרגילים שלי" as a room topic
    static final long STUCK_MS = 30_000;
    static final long KEEP_MS = 4 * 60 * 60_000L;
    static final int RETRY_AFTER = 2;
    /** 2026-11-22: "3, 2, 1" on every screen after "מתחילים" - questions open when it ends. */
    static final long COUNTDOWN_MS = 3_000;
    private long countdownMs = COUNTDOWN_MS;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setCountdownMs(@org.springframework.beans.factory.annotation.Value("${brainblocks.room-countdown-ms:3000}") long ms) {
        this.countdownMs = Math.max(0, ms);
    }
    /** 2026-11-22 (#7): this many wrong in a row = stuck, with the reason. */
    static final int WRONG_STUCK = 3;
    /** 2026-11-22 (#5): coins for the island - everyone who played, more for places 1-3. */
    static final int COINS_PLAYED = 5;
    static final int COINS_TOP3 = 10;
    static final String[] TEAM_NAMES = {"🐬 הדולפינים", "🦈 הכרישים"};

    static final class Player {
        final long id;
        final Long profileId;
        final String name;
        final String avatar;
        final String guestToken;
        final Grade grade;
        boolean approved, rejected;
        int points, answered, right, streak;
        // 2026-11-22: wrong in a row (stuck #7), group (#9: -1 = none), last room's result (#3), coins given (#5)
        int wrongStreak;
        int team = -1;
        Integer prevRight, prevAnswered;
        int coins;
        long lastActiveMs;
        QuestionResponse current;
        boolean currentIsRetry;
        final Deque<Object[]> retries = new ArrayDeque<>(); // {question, questions still to wait}
        final Set<String> seen = new HashSet<>();

        Player(long id, Long profileId, String name, String avatar, String guestToken, Grade grade) {
            this.id = id;
            this.profileId = profileId;
            this.name = name;
            this.avatar = avatar;
            this.guestToken = guestToken;
            this.grade = grade;
        }
    }

    static final class Room {
        final long id;
        final String code;
        final Long teacherId;
        final Long classId;
        final String title;
        final Grade grade;
        final List<String> topics;
        long durationMs;
        State state = State.LOBBY;
        long endsAt;
        // 2026-11-15 (Shir's room upgrade #8): the teacher pauses everyone; the clock stops (pausedAt).
        long pausedAt;
        final long createdAt;
        final Map<Long, Player> players = new LinkedHashMap<>();
        final Map<String, int[]> skills = new HashMap<>(); // skill -> {answered, right}
        // 2026-11-22: options the teacher chose, the countdown, and what the end-of-room summary needs
        boolean coins, teams, finalized;
        long startsAt;
        final Map<String, int[]> topicStats = new HashMap<>(); // topic -> {answered, right}
        final Map<String, Set<Long>> missed = new HashMap<>(); // exercise -> players who got it wrong
        long nextPlayer = 1;

        Room(long id, String code, Long teacherId, Long classId, String title, Grade grade, List<String> topics, long durationMs, long now) {
            this.id = id;
            this.code = code;
            this.teacherId = teacherId;
            this.classId = classId;
            this.title = title;
            this.grade = grade;
            this.topics = topics;
            this.durationMs = durationMs;
            this.createdAt = now;
        }
    }

    private final Map<Long, Room> rooms = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private long nextRoom = 1;
    private LongSupplier clock = System::currentTimeMillis;

    private final AccessService access;
    private final LearnClassRepository classes;
    private final LearnerProfileRepository profiles;
    private final LearnerAnswerRepository answers;
    private final TeacherQuestionRepository bank;
    private final LearningQuestionService questions;

    public LiveRoomService(AccessService access, LearnClassRepository classes, LearnerProfileRepository profiles,
                           LearnerAnswerRepository answers, TeacherQuestionRepository bank, LearningQuestionService questions) {
        this.access = access;
        this.classes = classes;
        this.profiles = profiles;
        this.answers = answers;
        this.bank = bank;
        this.questions = questions;
    }

    void setClock(LongSupplier clock) { this.clock = clock; }

    // 2026-11-22: optional so the snapshot test can build the service by hand
    private com.example.brainblocks.learning.repository.LearnRoomResultRepository results;
    private com.example.brainblocks.learning.repository.LearnIslandItemRepository islandItems;
    private com.example.brainblocks.learning.repository.LearnRoomTemplateRepository templates;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setResults(com.example.brainblocks.learning.repository.LearnRoomResultRepository results) { this.results = results; }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setIslandItems(com.example.brainblocks.learning.repository.LearnIslandItemRepository islandItems) { this.islandItems = islandItems; }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setTemplates(com.example.brainblocks.learning.repository.LearnRoomTemplateRepository templates) { this.templates = templates; }

    // ---- the teacher ----

    public synchronized Map<String, Object> create(Long me, Object rawClass, List<?> rawTopics, Object rawMinutes, Object rawGrade) {
        return create(me, rawClass, rawTopics, rawMinutes, rawGrade, false, false);
    }

    public synchronized Map<String, Object> create(Long me, Object rawClass, List<?> rawTopics, Object rawMinutes, Object rawGrade,
                                                   boolean coins, boolean teams) {
        LearnerProfile v = access.requireStaff(me);
        boolean admin = access.role(v) == AccessService.Role.ADMIN;
        Long classId = rawClass == null || rawClass.toString().isBlank() || "0".equals(rawClass.toString()) ? null : Long.valueOf(rawClass.toString());
        LearnClass c = null;
        if (classId != null) {
            c = classes.findById(classId).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "אין כיתה כזו"));
            if (!admin && !c.getTeacherId().equals(v.getId())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "זו לא כיתה שלך");
        }
        Grade grade = c != null && c.getGrade() != null ? c.getGrade() : parseGrade(rawGrade, v.getGrade());
        List<String> topics = new ArrayList<>();
        for (Object o : rawTopics == null ? List.of() : rawTopics) {
            String t = String.valueOf(o).trim().toUpperCase();
            if (MY.equals(t)) {
                if (bank.findByTeacherIdOrderByIdDesc(v.getId()).stream().noneMatch(q -> !q.isOff())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "עוד אין תרגילים שלך - הוסיפי קודם");
                }
            } else {
                Topic topic;
                try { topic = Topic.valueOf(t); } catch (IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "נושא לא מוכר"); }
                if (topic.isGame() || topic == Topic.GEOMETRY) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "הנושא הזה לא מתאים לחדר");
                if (!GradeTopics.allowed(topic, grade)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "הנושא לא שייך לכיתה הזאת");
            }
            if (!topics.contains(t)) topics.add(t);
        }
        if (topics.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "בחרי לפחות נושא אחד");
        int minutes = rawMinutes == null ? 5 : Integer.parseInt(rawMinutes.toString());
        if (minutes < 1 || minutes > 30) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "משך: 1 עד 30 דקות");
        cleanup();
        String code;
        do { code = String.valueOf(100_000 + random.nextInt(900_000)); } while (byCodeOrNull(code) != null);
        Room r = new Room(nextRoom++, code, v.getId(), classId, c != null ? c.getName() : v.getName(), grade, List.copyOf(topics), minutes * 60_000L, clock.getAsLong());
        r.coins = coins;
        r.teams = teams;
        rooms.put(r.id, r);
        return Map.of("id", r.id, "code", r.code);
    }

    public synchronized Map<String, Object> live(Long me, long roomId) {
        Room r = own(me, roomId);
        tick(r);
        long now = clock.getAsLong();
        List<Player> in = r.players.values().stream().filter(p -> p.approved).sorted(byScore()).toList();
        List<Map<String, Object>> rows = new ArrayList<>();
        int place = 0;
        for (Player p : in) {
            Map<String, Object> m = playerRow(p);
            m.put("place", ++place);
            String why = stuckWhy(r, p, now);
            m.put("stuck", why != null);
            m.put("stuckWhy", why);
            m.put("profileId", p.profileId);
            rows.add(m);
        }
        List<Map<String, Object>> waiting = new ArrayList<>();
        for (Player p : r.players.values()) if (!p.approved && !p.rejected) waiting.add(playerRow(p));
        List<Map<String, Object>> hard = new ArrayList<>();
        r.skills.entrySet().stream().filter(e -> e.getValue()[0] >= 3)
                .sorted(Comparator.comparingDouble(e -> e.getValue()[1] / (double) e.getValue()[0]))
                .limit(3)
                .forEach(e -> hard.add(Map.of("skill", Skills.name(e.getKey()), "pct", Math.round(100.0 * e.getValue()[1] / e.getValue()[0]))));
        int answered = in.stream().mapToInt(p -> p.answered).sum();
        int right = in.stream().mapToInt(p -> p.right).sum();
        Map<String, Object> out = header(r);
        out.put("players", rows);
        out.put("waiting", waiting);
        out.put("hardest", hard);
        out.put("answered", answered);
        out.put("pct", answered == 0 ? 0 : Math.round(100.0 * right / answered));
        out.put("guests", in.stream().filter(p -> p.profileId == null).count());
        out.put("classId", r.classId);
        out.put("teamScores", teamScores(r, in));
        if (r.state == State.ENDED) out.put("summary", summary(r, in));
        if (r.classId != null) {
            Set<Long> inside = new HashSet<>();
            for (Player p : r.players.values()) if (p.profileId != null && !p.rejected) inside.add(p.profileId);
            out.put("notYet", profiles.findAll().stream()
                    .filter(p -> r.classId.equals(p.getClassId()) && p.isApproved() && !p.isDisabled() && !p.isTeacher() && !p.isAdmin() && !inside.contains(p.getId()))
                    .map(LearnerProfile::getName).toList());
        } else {
            out.put("notYet", List.of());
        }
        return out;
    }

    /** The classroom board: only the top five (Shir: nobody at the bottom is shown in front of everyone). */
    public synchronized Map<String, Object> board(Long me, long roomId) {
        Room r = own(me, roomId);
        tick(r);
        List<Player> in = r.players.values().stream().filter(p -> p.approved).sorted(byScore()).toList();
        List<Map<String, Object>> top = new ArrayList<>();
        for (int i = 0; i < Math.min(5, in.size()); i++) {
            Map<String, Object> m = playerRow(in.get(i));
            m.put("place", i + 1);
            top.add(m);
        }
        int answered = in.stream().mapToInt(p -> p.answered).sum();
        int right = in.stream().mapToInt(p -> p.right).sum();
        Map<String, Object> out = header(r);
        out.put("top", top);
        out.put("count", in.size());
        out.put("teamScores", teamScores(r, in));
        out.put("pct", answered == 0 ? 0 : Math.round(100.0 * right / answered));
        return out;
    }

    public synchronized void decide(Long me, long roomId, long playerId, boolean approve) {
        Room r = own(me, roomId);
        Player p = r.players.get(playerId);
        if (p == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "אין ילד כזה בחדר");
        p.approved = approve;
        p.rejected = !approve;
    }

    public synchronized void start(Long me, long roomId) {
        Room r = own(me, roomId);
        if (r.state != State.LOBBY) return;
        r.state = State.RUNNING;
        r.startsAt = clock.getAsLong() + countdownMs;
        r.endsAt = r.startsAt + r.durationMs;
        if (r.teams) splitTeams(r);
    }

    public synchronized void extend(Long me, long roomId) {
        Room r = own(me, roomId);
        if (r.state == State.RUNNING) r.endsAt += 60_000;
        else if (r.state == State.LOBBY) r.durationMs += 60_000;
    }

    /** 2026-11-15 (#8): pause = every screen stops and the clock with it; resume = the same time is left. */
    public synchronized void pause(Long me, long roomId) {
        Room r = own(me, roomId);
        tick(r);
        if (r.state == State.RUNNING && r.pausedAt == 0) r.pausedAt = clock.getAsLong();
    }

    public synchronized void resume(Long me, long roomId) {
        Room r = own(me, roomId);
        if (r.state == State.RUNNING && r.pausedAt > 0) {
            r.endsAt += clock.getAsLong() - r.pausedAt;
            r.pausedAt = 0;
        }
    }

    public synchronized void end(Long me, long roomId) {
        Room r = own(me, roomId);
        r.state = State.ENDED;
        r.endsAt = Math.min(r.endsAt == 0 ? clock.getAsLong() : r.endsAt, clock.getAsLong());
        finish(r);
    }

    // ---- the teacher's own exercises ----

    public List<Map<String, Object>> myQuestions(Long me) {
        LearnerProfile v = access.requireStaff(me);
        List<Map<String, Object>> out = new ArrayList<>();
        for (TeacherQuestion q : bank.findByTeacherIdOrderByIdDesc(v.getId())) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", q.getId());
            m.put("text", q.getText());
            m.put("correct", q.getCorrect());
            m.put("wrong", wrongs(q));
            m.put("off", q.isOff());
            out.add(m);
        }
        return out;
    }

    public Map<String, Object> addQuestion(Long me, Map<String, Object> body) {
        LearnerProfile v = access.requireStaff(me);
        String text = clean(body.get("text"), 160, "השאלה");
        String correct = clean(body.get("correct"), 40, "התשובה הנכונה");
        List<String> wrong = new ArrayList<>();
        for (String k : List.of("wrong1", "wrong2", "wrong3")) {
            Object w = body.get(k);
            if (w != null && !w.toString().isBlank()) {
                String s = clean(w, 40, "תשובה שגויה");
                if (!s.equals(correct) && !wrong.contains(s)) wrong.add(s);
            }
        }
        if (wrong.isEmpty() && !correct.matches("-?\\d+")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "תשובה שאינה מספר צריכה לפחות תשובה שגויה אחת");
        }
        while (wrong.size() < 3) wrong.add(null);
        TeacherQuestion q = bank.save(new TeacherQuestion(v.getId(), text, correct, wrong.get(0), wrong.get(1), wrong.get(2)));
        return Map.of("id", q.getId());
    }

    public void setQuestionOff(Long me, long id, boolean off) {
        LearnerProfile v = access.requireStaff(me);
        TeacherQuestion q = bank.findById(id).filter(x -> x.getTeacherId().equals(v.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "אין תרגיל כזה"));
        q.setOff(off);
        bank.save(q);
    }

    // ---- the child / the guest ----

    public synchronized Map<String, Object> joinChild(Long me, Object rawCode) {
        LearnerProfile p = profiles.findById(me).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (p.isTeacher() || p.isAdmin()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "חדר משחק הוא לילדים - המורה פותחת אותו במסך הכיתות");
        Room r = byCode(rawCode);
        Player pl = r.players.values().stream().filter(x -> me.equals(x.profileId)).findFirst().orElse(null);
        if (pl == null) {
            pl = new Player(r.nextPlayer++, me, p.getName(), p.getAvatar() == null ? null : p.getAvatar().toString(), null, p.getGrade() != null ? p.getGrade() : r.grade);
            // A child of the room's class comes straight in; a child of another class waits for the teacher.
            // 2026-11-12: a room with no class (Shir opens one for "everyone" - there are no classes yet) lets
            // every logged-in child in; before, her sister waited forever for an approval no one saw.
            pl.approved = r.classId == null || r.classId.equals(p.getClassId());
            r.players.put(pl.id, pl);
            if (r.teams && r.state == State.RUNNING) pl.team = smallerTeam(r);
        }
        return me(r, pl);
    }

    public synchronized Map<String, Object> joinGuest(Object rawCode, Object rawName) {
        Room r = byCode(rawCode);
        String name = clean(rawName, 20, "השם");
        if (r.players.values().stream().filter(p -> p.profileId == null).count() >= 40) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "יש כבר הרבה אורחים בחדר");
        }
        byte[] b = new byte[24];
        random.nextBytes(b);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(b);
        Player pl = new Player(r.nextPlayer++, null, name, null, token, r.grade);
        r.players.put(pl.id, pl);
        if (r.teams && r.state == State.RUNNING) pl.team = smallerTeam(r);
        Map<String, Object> out = me(r, pl);
        out.put("guestToken", token);
        return out;
    }

    public synchronized Map<String, Object> stateForChild(Long me, Object rawCode) {
        Room r = byCode(rawCode);
        return me(r, childIn(r, me));
    }

    public synchronized Map<String, Object> stateForGuest(Object rawCode, String token) {
        Room r = byCode(rawCode);
        return me(r, guestIn(r, token));
    }

    public synchronized Map<String, Object> questionForChild(Long me, Object rawCode) {
        Room r = byCode(rawCode);
        return question(r, childIn(r, me));
    }

    public synchronized Map<String, Object> questionForGuest(Object rawCode, String token) {
        Room r = byCode(rawCode);
        return question(r, guestIn(r, token));
    }

    public synchronized Map<String, Object> answerForChild(Long me, Object rawCode, Object chosen) {
        Room r = byCode(rawCode);
        return answer(r, childIn(r, me), chosen);
    }

    public synchronized Map<String, Object> answerForGuest(Object rawCode, String token, Object chosen) {
        Room r = byCode(rawCode);
        return answer(r, guestIn(r, token), chosen);
    }

    // ---- the game ----

    private Map<String, Object> question(Room r, Player p) {
        tick(r);
        if (!p.approved || r.state != State.RUNNING) throw new ResponseStatusException(HttpStatus.CONFLICT, "המשחק עוד לא התחיל");
        if (r.pausedAt > 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "המורה עצרה רגע");
        if (clock.getAsLong() < r.startsAt) throw new ResponseStatusException(HttpStatus.CONFLICT, "מתחילים עוד רגע");
        if (p.current == null) next(r, p);
        p.lastActiveMs = clock.getAsLong();
        QuestionResponse q = p.current;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("text", q.getText());
        m.put("options", q.getOptions());
        m.put("visual", q.getVisual());
        m.put("topic", q.getTopic());
        m.put("retry", p.currentIsRetry);
        m.put("number", p.answered + 1);
        return m; // no correctIndex - the server checks
    }

    private Map<String, Object> answer(Room r, Player p, Object rawChosen) {
        tick(r);
        if (r.pausedAt > 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "המורה עצרה רגע");
        if (!p.approved || r.state != State.RUNNING || p.current == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, r.state == State.ENDED ? "המשחק נגמר" : "אין שאלה פתוחה");
        }
        QuestionResponse q = p.current;
        String right = q.getOptions().get(q.getCorrectIndex());
        String chosen = rawChosen == null ? "" : rawChosen.toString();
        boolean ok = right.equals(chosen);
        int gained = 0;
        if (!p.currentIsRetry) {
            p.answered++;
            String skill = q.getSkill() == null ? "OTHER" : q.getSkill();
            int[] s = r.skills.computeIfAbsent(skill, k -> new int[2]);
            s[0]++;
            int[] ts = r.topicStats.computeIfAbsent(String.valueOf(q.getTopic()), k -> new int[2]);
            ts[0]++;
            if (ok) ts[1]++;
            if (ok) {
                s[1]++;
                p.right++;
                p.wrongStreak = 0;
                p.streak++;
                gained = 10 + (p.streak >= 3 ? 5 : 0);
                p.points += gained;
            } else {
                p.streak = 0;
                p.wrongStreak++;
                r.missed.computeIfAbsent(q.getText(), k -> new HashSet<>()).add(p.id);
                p.retries.addLast(new Object[] {q, RETRY_AFTER}); // the same exercise again, 2 questions later
            }
            if (p.profileId != null) {
                answers.save(new LearnerAnswer(p.profileId, q.getTopic(), skill, 1, SessionMode.TIMED, ok, false,
                        q.getText(), chosen, right));
            }
        }
        p.current = null;
        p.lastActiveMs = clock.getAsLong();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("correct", ok);
        m.put("right", right);
        m.put("gained", gained);
        m.put("points", p.points);
        m.put("streak", p.streak);
        m.put("retry", p.currentIsRetry);
        return m;
    }

    private void next(Room r, Player p) {
        // A wrong answer's exercise comes back once two other questions passed (options reordered).
        for (Object[] w : p.retries) w[1] = (Integer) w[1] - 1;
        Object[] due = p.retries.stream().filter(w -> (Integer) w[1] < 0).findFirst().orElse(null);
        if (due != null) {
            p.retries.remove(due);
            QuestionResponse q = (QuestionResponse) due[0];
            p.current = reorder(q);
            p.currentIsRetry = true;
            return;
        }
        p.currentIsRetry = false;
        String topic = r.topics.get(random.nextInt(r.topics.size()));
        int stage = Math.min(6, 1 + p.right / 4); // harder as the child gets more right
        QuestionResponse q = MY.equals(topic) ? fromBank(r, p) : questions.generateFresh(Topic.valueOf(topic),
                p.grade, Difficulty.MEDIUM, stage, 1, p.seen).get(0);
        p.seen.add(LearningQuestionService.key(q));
        p.current = q;
    }

    private QuestionResponse fromBank(Room r, Player p) {
        List<TeacherQuestion> all = bank.findByTeacherIdOrderByIdDesc(r.teacherId).stream().filter(q -> !q.isOff()).toList();
        List<TeacherQuestion> fresh = all.stream().filter(q -> !p.seen.contains(LearningQuestionService.key(q.getText(), q.getCorrect()))).toList();
        TeacherQuestion t = (fresh.isEmpty() ? all : fresh).get(random.nextInt((fresh.isEmpty() ? all : fresh).size()));
        List<String> options = new ArrayList<>();
        options.add(t.getCorrect());
        options.addAll(wrongs(t));
        if (options.size() < 4 && t.getCorrect().matches("-?\\d+")) {
            int c = Integer.parseInt(t.getCorrect());
            for (int d : new int[] {1, -1, 10, 2, -10, -2}) {
                String w = String.valueOf(c + d);
                if (options.size() < 4 && !options.contains(w) && c + d >= 0) options.add(w);
            }
        }
        Collections.shuffle(options, random);
        return new QuestionResponse(0, Topic.MIXED, t.getText(), options, options.indexOf(t.getCorrect()), null, null, null)
                .withSkill("TEACHER_OWN");
    }

    private QuestionResponse reorder(QuestionResponse q) {
        String right = q.getOptions().get(q.getCorrectIndex());
        List<String> options = new ArrayList<>(q.getOptions());
        for (int i = 0; i < 5; i++) {
            Collections.shuffle(options, random);
            if (options.indexOf(right) != q.getCorrectIndex()) break;
        }
        return new QuestionResponse(q.getId(), q.getTopic(), q.getText(), options, options.indexOf(right), q.getHint(), q.getExplanation(), q.getVisual())
                .withSkill(q.getSkill());
    }

    // ---- helpers ----

    private Map<String, Object> me(Room r, Player p) {
        tick(r);
        Map<String, Object> m = header(r);
        m.put("playerId", p.id);
        m.put("name", p.name);
        m.put("status", p.rejected ? "REJECTED" : p.approved ? "IN" : "WAITING");
        m.put("points", p.points);
        m.put("answered", p.answered);
        m.put("right", p.right);
        m.put("inRoom", r.players.values().stream().filter(x -> x.approved).count());
        m.put("streak", p.streak);
        // 2026-11-15 (#1): the place among the approved players (1 = first).
        List<Player> ranked = r.players.values().stream().filter(x -> x.approved).sorted(byScore()).toList();
        m.put("place", ranked.indexOf(p) + 1);
        m.put("team", p.team >= 0 ? TEAM_NAMES[p.team] : null);
        if (r.state == State.ENDED) {
            m.put("prevRight", p.prevRight);
            m.put("prevAnswered", p.prevAnswered);
            m.put("improved", p.prevRight != null && p.right > p.prevRight);
            m.put("coins", p.coins);
        }
        return m;
    }

    private Map<String, Object> header(Room r) {
        long now = clock.getAsLong();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.id);
        m.put("code", r.code);
        m.put("title", r.title);
        m.put("state", r.state.name());
        m.put("topics", r.topics);
        m.put("minutes", r.durationMs / 60_000);
        // during "3, 2, 1" the clock shows the full time (it starts when the count ends)
        long at = r.pausedAt > 0 ? r.pausedAt : Math.max(now, r.startsAt);
        m.put("secondsLeft", r.state == State.RUNNING ? Math.max(0, (r.endsAt - at + 999) / 1000) : r.state == State.LOBBY ? r.durationMs / 1000 : 0);
        m.put("paused", r.state == State.RUNNING && r.pausedAt > 0);
        m.put("countdownMs", r.state == State.RUNNING ? Math.max(0, r.startsAt - now) : 0);
        m.put("teams", r.teams);
        m.put("coins", r.coins);
        return m;
    }

    private static Map<String, Object> playerRow(Player p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.id);
        m.put("name", p.name);
        m.put("avatar", p.avatar);
        m.put("guest", p.profileId == null);
        m.put("points", p.points);
        m.put("answered", p.answered);
        m.put("right", p.right);
        return m;
    }

    private static Comparator<Player> byScore() {
        return Comparator.comparingInt((Player p) -> -p.points).thenComparingInt(p -> -p.right).thenComparingLong(p -> p.id);
    }

    private void tick(Room r) {
        if (r.state == State.RUNNING && r.pausedAt == 0 && clock.getAsLong() >= r.endsAt) r.state = State.ENDED;
        if (r.state == State.ENDED) finish(r);
    }

    // ---- 2026-11-22: stage two of Shir's room upgrades ----

    /** #7: why a child looks stuck right now (null = fine). */
    private String stuckWhy(Room r, Player p, long now) {
        if (r.state != State.RUNNING || r.pausedAt > 0 || now < r.startsAt) return null;
        if (p.wrongStreak >= WRONG_STUCK) return p.wrongStreak + " טעויות ברצף";
        if (p.lastActiveMs > 0 && now - p.lastActiveMs > STUCK_MS) return "לא ענה/תה " + (now - p.lastActiveMs) / 1000 + " שניות";
        return null;
    }

    /** #9: two groups, balanced by how well each child did lately (snake order), guests last. */
    private void splitTeams(Room r) {
        List<Player> in = new ArrayList<>(r.players.values().stream().filter(p -> p.approved).toList());
        Map<Long, Double> level = new HashMap<>();
        for (Player p : in) level.put(p.id, recentAccuracy(p));
        in.sort(Comparator.comparingDouble((Player p) -> -level.get(p.id)).thenComparingLong(p -> p.id));
        for (int i = 0; i < in.size(); i++) in.get(i).team = (i % 4 == 0 || i % 4 == 3) ? 0 : 1;
    }

    private double recentAccuracy(Player p) {
        if (p.profileId == null || answers == null) return 0.5;
        List<LearnerAnswer> last = answers.findTop600ByProfileIdOrderByIdDesc(p.profileId);
        int n = Math.min(60, last.size());
        if (n == 0) return 0.5;
        long right = last.subList(0, n).stream().filter(LearnerAnswer::isCorrect).count();
        return right / (double) n;
    }

    private int smallerTeam(Room r) {
        long a = r.players.values().stream().filter(p -> p.approved && p.team == 0).count();
        long b = r.players.values().stream().filter(p -> p.approved && p.team == 1).count();
        return a <= b ? 0 : 1;
    }

    private static List<Map<String, Object>> teamScores(Room r, List<Player> in) {
        if (!r.teams) return List.of();
        List<Map<String, Object>> out = new ArrayList<>();
        for (int t = 0; t < 2; t++) {
            final int team = t;
            List<Player> mine = in.stream().filter(p -> p.team == team).toList();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", TEAM_NAMES[t]);
            m.put("points", mine.stream().mapToInt(p -> p.points).sum());
            m.put("players", mine.size());
            out.add(m);
        }
        return out;
    }

    /** #3 + #5: once per room - each child's result is kept (for "השתפרתי" next time) and coins are given. */
    private void finish(Room r) {
        if (r.finalized) return;
        r.finalized = true;
        String key = r.code + "-" + (r.createdAt / 1000);
        List<Player> ranked = r.players.values().stream().filter(p -> p.approved).sorted(byScore()).toList();
        for (int i = 0; i < ranked.size(); i++) {
            Player p = ranked.get(i);
            if (p.profileId == null || p.answered == 0) continue;
            try {
                if (results != null) {
                    results.findFirstByProfileIdOrderByIdDesc(p.profileId).ifPresent(prev -> {
                        p.prevRight = prev.getRightCount();
                        p.prevAnswered = prev.getAnswered();
                    });
                    if (!results.existsByProfileIdAndRoomKey(p.profileId, key)) {
                        results.save(new com.example.brainblocks.learning.model.LearnRoomResult(p.profileId, key, p.answered, p.right, p.points, i + 1));
                    }
                }
                if (r.coins && islandItems != null) {
                    int coins = COINS_PLAYED + (i < 3 ? COINS_TOP3 : 0);
                    islandItems.save(new com.example.brainblocks.learning.model.LearnIslandItem(p.profileId, "room:" + key, 0, coins));
                    p.coins = coins;
                }
            } catch (RuntimeException e) {
                // the unique key: already saved (a restart in between) - nothing more to do for this child
                org.slf4j.LoggerFactory.getLogger(LiveRoomService.class).info("room result already kept: {}", e.getClass().getSimpleName());
            }
        }
    }

    /** #6: what the teacher sees when the room ended - what was hard, what to practise, who needs help. */
    private Map<String, Object> summary(Room r, List<Player> in) {
        Map<String, Object> out = new LinkedHashMap<>();
        List<Map<String, Object>> exercises = new ArrayList<>();
        r.missed.entrySet().stream()
                .filter(e -> e.getValue().size() >= 2)
                .sorted(Comparator.comparingInt((Map.Entry<String, Set<Long>> e) -> -e.getValue().size()))
                .limit(3)
                .forEach(e -> exercises.add(Map.of("text", e.getKey(), "kids", e.getValue().size())));
        out.put("missed", exercises);
        String hardTopic = r.topicStats.entrySet().stream()
                .filter(e -> e.getValue()[0] >= 3 && !"MIXED".equals(e.getKey()))
                .min(Comparator.comparingDouble(e -> e.getValue()[1] / (double) e.getValue()[0]))
                .map(Map.Entry::getKey).orElse(null);
        out.put("hardTopic", hardTopic);
        if (hardTopic != null) {
            int[] t = r.topicStats.get(hardTopic);
            out.put("hardTopicPct", Math.round(100.0 * t[1] / t[0]));
        }
        List<Map<String, Object>> help = new ArrayList<>();
        List<Map<String, Object>> better = new ArrayList<>();
        for (Player p : in) {
            if (p.answered >= 3 && p.right * 2 < p.answered) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("name", p.name);
                m.put("profileId", p.profileId);
                m.put("pct", Math.round(100.0 * p.right / p.answered));
                help.add(m);
            }
            if (p.prevRight != null && p.right > p.prevRight) better.add(Map.of("name", p.name, "plus", p.right - p.prevRight));
        }
        out.put("needHelp", help);
        out.put("improved", better);
        out.put("coinsGiven", r.coins);
        return out;
    }

    // ---- #10: rooms saved as templates ----

    public List<Map<String, Object>> templates(Long me) {
        LearnerProfile v = access.requireStaff(me);
        List<Map<String, Object>> out = new ArrayList<>();
        if (templates == null) return out;
        for (var t : templates.findByTeacherIdOrderByIdDesc(v.getId())) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId());
            m.put("name", t.getName());
            m.put("classId", t.getClassId());
            m.put("grade", t.getGrade());
            m.put("topics", List.of(t.getTopics().split(",")));
            m.put("minutes", t.getMinutes());
            m.put("coins", t.isCoins());
            m.put("teams", t.isTeams());
            out.add(m);
        }
        return out;
    }

    public Map<String, Object> saveTemplate(Long me, Map<String, Object> body) {
        LearnerProfile v = access.requireStaff(me);
        if (templates == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
        if (templates.countByTeacherId(v.getId()) >= 30) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "אפשר לשמור עד 30 חדרים");
        String name = clean(body.get("name"), 40, "שם החדר");
        Object rawTopics = body.get("topics");
        List<String> topics = new ArrayList<>();
        if (rawTopics instanceof List<?> l) for (Object o : l) {
            String t = String.valueOf(o).trim().toUpperCase();
            if (!MY.equals(t)) {
                try { Topic.valueOf(t); } catch (IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "נושא לא מוכר"); }
            }
            if (!topics.contains(t)) topics.add(t);
        }
        if (topics.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "בחרי לפחות נושא אחד");
        int minutes = body.get("minutes") == null ? 5 : Integer.parseInt(body.get("minutes").toString());
        if (minutes < 1 || minutes > 30) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "משך: 1 עד 30 דקות");
        Object rawClass = body.get("classId");
        Long classId = rawClass == null || rawClass.toString().isBlank() || "0".equals(rawClass.toString()) ? null : Long.valueOf(rawClass.toString());
        if (classId != null) {
            LearnClass c = classes.findById(classId).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "אין כיתה כזו"));
            if (access.role(v) != AccessService.Role.ADMIN && !c.getTeacherId().equals(v.getId())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "זו לא כיתה שלך");
        }
        String grade = body.get("grade") == null ? null : parseGrade(body.get("grade"), v.getGrade()).name();
        var t = templates.save(new com.example.brainblocks.learning.model.LearnRoomTemplate(v.getId(), name, classId, grade,
                String.join(",", topics), minutes, Boolean.TRUE.equals(body.get("coins")), Boolean.TRUE.equals(body.get("teams"))));
        return Map.of("id", t.getId());
    }

    public void deleteTemplate(Long me, long id) {
        LearnerProfile v = access.requireStaff(me);
        if (templates == null) return;
        var t = templates.findById(id).filter(x -> x.getTeacherId().equals(v.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "אין חדר שמור כזה"));
        templates.delete(t);
    }

    /** Opens a room from a saved one - the same checks as a new room. */
    public synchronized Map<String, Object> openTemplate(Long me, long id) {
        LearnerProfile v = access.requireStaff(me);
        if (templates == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
        var t = templates.findById(id).filter(x -> x.getTeacherId().equals(v.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "אין חדר שמור כזה"));
        return create(me, t.getClassId(), List.of(t.getTopics().split(",")), t.getMinutes(), t.getGrade(), t.isCoins(), t.isTeams());
    }

    /**
     * 2026-11-22 (Shir: "warm up before a room"): the first questions after a deploy took 4-12 s (cold code
     * paths). Generating one question per topic of a grade wakes them up; called when the teacher opens the
     * "new room" page and once when the server starts.
     */
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    void warmUpAfterStart() {
        Thread t = new Thread(() -> {
            for (Grade g : Grade.values()) warmUp(g);
            if (answers != null) answers.count(); // the database connection too
        }, "room-warmup");
        t.setDaemon(true);
        t.start();
    }

    public void warmUp(Grade grade) {
        Grade g = grade == null ? Grade.G4 : grade;
        for (Topic t : Topic.values()) {
            if (t.isGame() || t == Topic.GEOMETRY || !GradeTopics.allowed(t, g)) continue;
            try {
                questions.generateFresh(t, g, Difficulty.MEDIUM, 1, 1, new HashSet<>());
            } catch (RuntimeException ignored) {
                // a topic that can't make a question right now just stays cold
            }
        }
    }

    private Room own(Long me, long roomId) {
        LearnerProfile v = access.requireStaff(me);
        Room r = rooms.get(roomId);
        if (r == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "החדר לא קיים (אולי נסגר)");
        if (access.role(v) != AccessService.Role.ADMIN && !r.teacherId.equals(v.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "זה לא החדר שלך");
        }
        return r;
    }

    private Room byCodeOrNull(String code) {
        for (Room r : rooms.values()) if (r.code.equals(code) && r.state != State.ENDED) return r;
        for (Room r : rooms.values()) if (r.code.equals(code)) return r;
        return null;
    }

    private Room byCode(Object rawCode) {
        String code = rawCode == null ? "" : rawCode.toString().replaceAll("\\D", "");
        Room r = byCodeOrNull(code);
        if (r == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "אין חדר עם הקוד הזה");
        return r;
    }

    private Player childIn(Room r, Long me) {
        return r.players.values().stream().filter(p -> me.equals(p.profileId)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "עוד לא נכנסת לחדר"));
    }

    private Player guestIn(Room r, String token) {
        if (token == null || token.isBlank()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return r.players.values().stream().filter(p -> token.equals(p.guestToken)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private void cleanup() {
        long now = clock.getAsLong();
        rooms.values().removeIf(r -> now - r.createdAt > KEEP_MS);
    }

    // ---- 2026-11-12: rooms survive a server restart ----
    // (Shir's sister typed the code of a room that a deploy had just wiped.) Every 2 seconds, if anything
    // changed, the rooms are written to a small JSON file; on start they are read back. Kept: the room,
    // its players (approved / points / answered / right / what they already saw), the skills table. Not
    // kept: the question on screen at that moment - the child just gets a new one. Off when no file is set
    // (tests); the run script sets brainblocks.rooms-file.

    private java.nio.file.Path snapshotFile;
    private final com.fasterxml.jackson.databind.ObjectMapper json = new com.fasterxml.jackson.databind.ObjectMapper();
    private String lastSnapshot = "";
    private java.util.concurrent.ScheduledExecutorService saver;

    @org.springframework.beans.factory.annotation.Autowired
    void setSnapshotFile(@org.springframework.beans.factory.annotation.Value("${brainblocks.rooms-file:}") String file) {
        this.snapshotFile = file == null || file.isBlank() || "off".equals(file) ? null : java.nio.file.Path.of(file);
    }

    @jakarta.annotation.PostConstruct
    void restoreAndStartSaving() {
        if (snapshotFile == null) return;
        restore();
        saver = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "rooms-snapshot");
            t.setDaemon(true);
            return t;
        });
        saver.scheduleWithFixedDelay(this::saveIfChanged, 2, 2, java.util.concurrent.TimeUnit.SECONDS);
    }

    @jakarta.annotation.PreDestroy
    void saveOnStop() {
        if (saver != null) saver.shutdownNow();
        saveIfChanged();
    }

    synchronized void saveIfChanged() {
        if (snapshotFile == null) return;
        try {
            cleanup();
            String now = json.writeValueAsString(snapshot());
            if (now.equals(lastSnapshot)) return;
            java.nio.file.Path tmp = snapshotFile.resolveSibling(snapshotFile.getFileName() + ".tmp");
            java.nio.file.Files.writeString(tmp, now);
            java.nio.file.Files.move(tmp, snapshotFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            lastSnapshot = now;
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(LiveRoomService.class).warn("rooms snapshot not saved: {}", e.getMessage());
        }
    }

    private List<Map<String, Object>> snapshot() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Room r : rooms.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.id);
            m.put("code", r.code);
            m.put("teacherId", r.teacherId);
            m.put("classId", r.classId);
            m.put("title", r.title);
            m.put("grade", r.grade == null ? null : r.grade.name());
            m.put("topics", r.topics);
            m.put("durationMs", r.durationMs);
            m.put("state", r.state.name());
            m.put("endsAt", r.endsAt);
            m.put("pausedAt", r.pausedAt);
            m.put("createdAt", r.createdAt);
            m.put("nextPlayer", r.nextPlayer);
            m.put("skills", r.skills);
            m.put("coins", r.coins);
            m.put("teams", r.teams);
            m.put("finalized", r.finalized);
            m.put("startsAt", r.startsAt);
            m.put("topicStats", r.topicStats);
            List<Map<String, Object>> ps = new ArrayList<>();
            for (Player p : r.players.values()) {
                Map<String, Object> pm = new LinkedHashMap<>();
                pm.put("id", p.id);
                pm.put("profileId", p.profileId);
                pm.put("name", p.name);
                pm.put("avatar", p.avatar);
                pm.put("guestToken", p.guestToken);
                pm.put("grade", p.grade == null ? null : p.grade.name());
                pm.put("approved", p.approved);
                pm.put("rejected", p.rejected);
                pm.put("points", p.points);
                pm.put("answered", p.answered);
                pm.put("right", p.right);
                pm.put("streak", p.streak);
                pm.put("wrongStreak", p.wrongStreak);
                pm.put("team", p.team);
                pm.put("coinsGot", p.coins);
                pm.put("prevRight", p.prevRight);
                pm.put("prevAnswered", p.prevAnswered);
                pm.put("lastActiveMs", p.lastActiveMs);
                pm.put("seen", new ArrayList<>(p.seen));
                ps.add(pm);
            }
            m.put("players", ps);
            out.add(m);
        }
        out.sort(Comparator.comparingLong(m -> (Long) m.get("id")));
        return out;
    }

    synchronized void restore() {
        try {
            if (snapshotFile == null || !java.nio.file.Files.isRegularFile(snapshotFile)) return;
            com.fasterxml.jackson.databind.JsonNode all = json.readTree(java.nio.file.Files.readString(snapshotFile));
            long now = clock.getAsLong();
            for (com.fasterxml.jackson.databind.JsonNode m : all) {
                if (now - m.path("createdAt").asLong() > KEEP_MS) continue;
                List<String> topics = new ArrayList<>();
                m.path("topics").forEach(t -> topics.add(t.asText()));
                Room r = new Room(m.path("id").asLong(), m.path("code").asText(), nullableLong(m, "teacherId"),
                        nullableLong(m, "classId"), m.path("title").asText(), grade(m.path("grade")), List.copyOf(topics),
                        m.path("durationMs").asLong(), m.path("createdAt").asLong());
                r.state = State.valueOf(m.path("state").asText("LOBBY"));
                r.endsAt = m.path("endsAt").asLong();
                r.pausedAt = m.path("pausedAt").asLong(0);
                r.nextPlayer = m.path("nextPlayer").asLong(1);
                r.coins = m.path("coins").asBoolean(false);
                r.teams = m.path("teams").asBoolean(false);
                r.finalized = m.path("finalized").asBoolean(false);
                r.startsAt = m.path("startsAt").asLong(0);
                m.path("topicStats").fields().forEachRemaining(e ->
                        r.topicStats.put(e.getKey(), new int[] {e.getValue().path(0).asInt(), e.getValue().path(1).asInt()}));
                m.path("skills").fields().forEachRemaining(e ->
                        r.skills.put(e.getKey(), new int[] {e.getValue().path(0).asInt(), e.getValue().path(1).asInt()}));
                for (com.fasterxml.jackson.databind.JsonNode pm : m.path("players")) {
                    Player p = new Player(pm.path("id").asLong(), nullableLong(pm, "profileId"), pm.path("name").asText(),
                            pm.path("avatar").isNull() ? null : pm.path("avatar").asText(null),
                            pm.path("guestToken").isNull() ? null : pm.path("guestToken").asText(null), grade(pm.path("grade")));
                    p.approved = pm.path("approved").asBoolean();
                    p.rejected = pm.path("rejected").asBoolean();
                    p.points = pm.path("points").asInt();
                    p.answered = pm.path("answered").asInt();
                    p.right = pm.path("right").asInt();
                    p.streak = pm.path("streak").asInt();
                    p.wrongStreak = pm.path("wrongStreak").asInt(0);
                    p.team = pm.path("team").asInt(-1);
                    p.coins = pm.path("coinsGot").asInt(0);
                    if (pm.path("prevRight").isNumber()) p.prevRight = pm.path("prevRight").asInt();
                    if (pm.path("prevAnswered").isNumber()) p.prevAnswered = pm.path("prevAnswered").asInt();
                    p.lastActiveMs = pm.path("lastActiveMs").asLong();
                    pm.path("seen").forEach(s -> p.seen.add(s.asText()));
                    r.players.put(p.id, p);
                }
                rooms.put(r.id, r);
                nextRoom = Math.max(nextRoom, r.id + 1);
            }
            lastSnapshot = json.writeValueAsString(snapshot());
            org.slf4j.LoggerFactory.getLogger(LiveRoomService.class).info("rooms restored after restart: {}", rooms.size());
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(LiveRoomService.class).warn("rooms snapshot not read: {}", e.getMessage());
        }
    }

    private static Long nullableLong(com.fasterxml.jackson.databind.JsonNode n, String field) {
        return n.path(field).isNumber() ? n.path(field).asLong() : null;
    }

    private static Grade grade(com.fasterxml.jackson.databind.JsonNode n) {
        try {
            return n.isTextual() ? Grade.valueOf(n.asText()) : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Tests: the snapshot file, and a second service reading it back as a restart would. */
    void useSnapshotFile(java.nio.file.Path file) {
        this.snapshotFile = file;
    }

    void putForTest(Room r) {
        rooms.put(r.id, r);
    }

    Room roomForTest(String code) {
        return byCodeOrNull(code);
    }

    private static List<String> wrongs(TeacherQuestion q) {
        List<String> w = new ArrayList<>();
        for (String s : new String[] {q.getWrong1(), q.getWrong2(), q.getWrong3()}) if (s != null && !s.isBlank()) w.add(s);
        return w;
    }

    private static String clean(Object raw, int max, String what) {
        // 2026-11-21 (security pass): hidden / direction-flipping characters out, like every name
        String s = raw == null || raw.toString().isBlank() ? "" : Names.clean(raw, max + 1, what);
        if (s.isEmpty() || s.length() > max) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, what + ": 1 עד " + max + " תווים");
        return s;
    }

    private static Grade parseGrade(Object raw, Grade fallback) {
        if (raw == null || raw.toString().isBlank()) return fallback == null ? Grade.G3 : fallback;
        try { return Grade.valueOf(raw.toString().trim().toUpperCase()); } catch (IllegalArgumentException e) { return fallback == null ? Grade.G3 : fallback; }
    }
}
