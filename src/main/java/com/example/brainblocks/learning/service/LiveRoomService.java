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

    static final class Player {
        final long id;
        final Long profileId;
        final String name;
        final String avatar;
        final String guestToken;
        final Grade grade;
        boolean approved, rejected;
        int points, answered, right, streak;
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
        final long createdAt;
        final Map<Long, Player> players = new LinkedHashMap<>();
        final Map<String, int[]> skills = new HashMap<>(); // skill -> {answered, right}
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

    // ---- the teacher ----

    public synchronized Map<String, Object> create(Long me, Object rawClass, List<?> rawTopics, Object rawMinutes, Object rawGrade) {
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
            m.put("stuck", r.state == State.RUNNING && p.lastActiveMs > 0 && now - p.lastActiveMs > STUCK_MS);
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
        r.endsAt = clock.getAsLong() + r.durationMs;
    }

    public synchronized void extend(Long me, long roomId) {
        Room r = own(me, roomId);
        if (r.state == State.RUNNING) r.endsAt += 60_000;
        else if (r.state == State.LOBBY) r.durationMs += 60_000;
    }

    public synchronized void end(Long me, long roomId) {
        Room r = own(me, roomId);
        r.state = State.ENDED;
        r.endsAt = Math.min(r.endsAt == 0 ? clock.getAsLong() : r.endsAt, clock.getAsLong());
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
            if (ok) {
                s[1]++;
                p.right++;
                p.streak++;
                gained = 10 + (p.streak >= 3 ? 5 : 0);
                p.points += gained;
            } else {
                p.streak = 0;
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
        m.put("secondsLeft", r.state == State.RUNNING ? Math.max(0, (r.endsAt - now + 999) / 1000) : r.state == State.LOBBY ? r.durationMs / 1000 : 0);
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
        if (r.state == State.RUNNING && clock.getAsLong() >= r.endsAt) r.state = State.ENDED;
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

    private static List<String> wrongs(TeacherQuestion q) {
        List<String> w = new ArrayList<>();
        for (String s : new String[] {q.getWrong1(), q.getWrong2(), q.getWrong3()}) if (s != null && !s.isBlank()) w.add(s);
        return w;
    }

    private static String clean(Object raw, int max, String what) {
        String s = raw == null ? "" : raw.toString().strip().replaceAll("\\s+", " ");
        if (s.isEmpty() || s.length() > max) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, what + ": 1 עד " + max + " תווים");
        return s;
    }

    private static Grade parseGrade(Object raw, Grade fallback) {
        if (raw == null || raw.toString().isBlank()) return fallback == null ? Grade.G3 : fallback;
        try { return Grade.valueOf(raw.toString().trim().toUpperCase()); } catch (IllegalArgumentException e) { return fallback == null ? Grade.G3 : fallback; }
    }
}
