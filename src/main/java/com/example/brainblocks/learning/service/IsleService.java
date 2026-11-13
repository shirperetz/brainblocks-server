package com.example.brainblocks.learning.service;

import com.example.brainblocks.learning.dto.request.AnswerDto;
import com.example.brainblocks.learning.model.Grade;
import com.example.brainblocks.learning.model.LearnIsleProgress;
import com.example.brainblocks.learning.model.LearnerAnswer;
import com.example.brainblocks.learning.model.LearnerProfile;
import com.example.brainblocks.learning.model.LearnerSession;
import com.example.brainblocks.learning.model.LearnerStageProgress;
import com.example.brainblocks.learning.model.SessionMode;
import com.example.brainblocks.learning.model.StageStatus;
import com.example.brainblocks.learning.model.Topic;
import com.example.brainblocks.learning.repository.LearnIsleProgressRepository;
import com.example.brainblocks.learning.repository.LearnerAnswerRepository;
import com.example.brainblocks.learning.repository.LearnerProfileRepository;
import com.example.brainblocks.learning.repository.LearnerSessionRepository;
import com.example.brainblocks.learning.repository.LearnerStageProgressRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Set;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 2026-11-13 (Shir: "an island for every subject, endless - when they finish one, another island
 * with other exercises; everything saved on the server"). docs/plan-infinite-islands.md.
 * <p>
 * The sea: the base islands (built by hand, in order), then generated islands "gen-1", "gen-2", ...
 * forever. Island i+1 opens when 2/3 of island i's stages are passed (12 of 18). A generated island N
 * always has the same name, colour and three games (from all the base games, rotating), played at
 * tier N - the games make bigger numbers as the tier grows. Every answer of every round is kept
 * (learn_answer), and the stages of generated islands in learn_isle_progress.
 */
@Service
public class IsleService {

    public static final int STAGES = 6;

    /** One island. games = the server topics of its games; tier 0 = a base island. */
    public record Isle(String key, String name, String mark, String color, int tier, List<Topic> games) {}

    /** The hand-built islands, in the order they open (the not-built ones are added here when ready). */
    static final List<Isle> BASE = List.of(
            new Isle("numbers", "אי המספרים", "1-20", "#25c685", 0, List.of(Topic.DRAGON, Topic.BALLOONS, Topic.BRIDGE, Topic.TRAIN)),
            new Isle("clock", "אי השעון והכסף", "12:00", "#ff9f43", 0, List.of(Topic.CLOCK, Topic.SHOP, Topic.COINS)),
            // 2026-11-13 (Shir: "the 3 missing islands")
            new Isle("shapes", "אי הצורות", "▲●■", "#4aa8ff", 0, List.of(Topic.SHAPE_NAME, Topic.SHAPE_SIDES, Topic.SHAPE_PATTERN)),
            new Isle("times", "אי הכפל והחילוק", "×÷", "#a77bff", 0, List.of(Topic.GROUPS, Topic.SHARING, Topic.ARRAYS)),
            new Isle("big", "אי המספרים הגדולים", "1000", "#ff6fb5", 0, List.of(Topic.PLACE_VALUE, Topic.NUMBER_LINE, Topic.BIGGER)));

    /**
     * 2026-11-13 (Shir): who each base island is made for. An island not for the child's grade is not
     * in their chain at all (it doesn't block them), and their generated islands use only their games.
     * No grade / ד׳ and up: every island.
     */
    private static final Map<String, Set<Grade>> GRADES = Map.of(
            "numbers", EnumSet.of(Grade.G1, Grade.G2),
            "clock", EnumSet.of(Grade.G1, Grade.G2, Grade.G3),
            "shapes", EnumSet.of(Grade.G1, Grade.G2, Grade.G3),
            "times", EnumSet.of(Grade.G2, Grade.G3),
            "big", EnumSet.of(Grade.G3));

    /** The base islands of this grade, in order. */
    public static List<Isle> chain(Grade grade) {
        if (grade != Grade.G1 && grade != Grade.G2 && grade != Grade.G3) return BASE;
        return BASE.stream().filter(b -> GRADES.get(b.key()).contains(grade)).toList();
    }

    private static final String[] GEN_NAMES = {"אי ההרפתקאות", "אי האוצר", "אי הכוכבים", "אי הענקים", "אי הקשת",
            "אי הר הגעש", "אי הפנינים", "אי הסודות", "אי הרוח", "אי הדרקונים"};
    private static final String[] GEN_MARKS = {"★", "◆", "✦", "▲", "●", "■", "♥", "✚", "☾", "☀"};
    private static final String[] GEN_COLORS = {"#4aa8ff", "#a77bff", "#ff6fb5", "#2fbf71", "#ff9f43", "#e0533d", "#14b8c4", "#7a5af8", "#f2b705", "#ff5f8f"};

    /** Generated island N (1, 2, ...), every grade's games: always the same for the same N. */
    public static Isle generated(int n) {
        return generated(n, null);
    }

    /** Generated island N for a grade: only that grade's games; always the same for the same N and grade. */
    public static Isle generated(int n, Grade grade) {
        if (n < 1) throw new IllegalArgumentException("n");
        List<Topic> pool = new ArrayList<>();
        for (Isle b : chain(grade)) pool.addAll(b.games());
        List<Topic> games = new ArrayList<>(3);
        int start = ((n - 1) * 3) % pool.size();
        for (int i = 0; games.size() < 3 && i < pool.size(); i++) {
            Topic t = pool.get((start + i) % pool.size());
            if (!games.contains(t)) games.add(t);
        }
        int i = (n - 1) % GEN_NAMES.length;
        int round = (n - 1) / GEN_NAMES.length;
        String name = GEN_NAMES[i] + (round > 0 ? " " + (round + 1) : "");
        return new Isle("gen-" + n, name, GEN_MARKS[i], GEN_COLORS[(n - 1) % GEN_COLORS.length], n, List.copyOf(games));
    }

    /** The island with this key, or null. */
    public static Isle byKey(String key) {
        return byKey(key, null);
    }

    /** The island with this key in this grade's sea, or null (also a base island of another grade). */
    public static Isle byKey(String key, Grade grade) {
        if (key == null) return null;
        for (Isle b : chain(grade)) if (b.key().equals(key)) return b;
        if (key.matches("gen-[1-9]\\d{0,4}")) return generated(Integer.parseInt(key.substring(4)), grade);
        return null;
    }

    /** Stages to pass before the next island opens: 2/3 (12 of 18, 16 of 24). */
    public static int needed(Isle isle) {
        int total = isle.games().size() * STAGES;
        return (total * 2 + 2) / 3;
    }

    private final LearnerStageProgressRepository stages;
    private final LearnIsleProgressRepository isleProgress;
    private final LearnerProfileRepository profiles;
    private final LearnerSessionRepository sessions;
    private final LearnerAnswerRepository answers;

    public IsleService(LearnerStageProgressRepository stages, LearnIsleProgressRepository isleProgress,
                       LearnerProfileRepository profiles, LearnerSessionRepository sessions, LearnerAnswerRepository answers) {
        this.stages = stages;
        this.isleProgress = isleProgress;
        this.profiles = profiles;
        this.sessions = sessions;
        this.answers = answers;
    }

    /** topic -> stage -> {stars, passed?1:0}, for one island of one child. */
    private Map<Topic, Map<Integer, int[]>> progressOf(Isle isle, List<LearnerStageProgress> base, List<LearnIsleProgress> gen) {
        Map<Topic, Map<Integer, int[]>> out = new HashMap<>();
        for (Topic t : isle.games()) out.put(t, new HashMap<>());
        if (isle.tier() == 0) {
            for (LearnerStageProgress s : base) {
                if (out.containsKey(s.getTopic())) {
                    out.get(s.getTopic()).put(s.getStageNumber(), new int[] {s.getStarsEarned(), s.getStatus() == StageStatus.COMPLETED ? 1 : 0});
                }
            }
        } else {
            for (LearnIsleProgress p : gen) {
                if (!p.getIsleKey().equals(isle.key())) continue;
                try {
                    Topic t = Topic.valueOf(p.getTopic());
                    if (out.containsKey(t)) out.get(t).put(p.getStageNumber(), new int[] {p.getStarsEarned(), p.isPassed() ? 1 : 0});
                } catch (IllegalArgumentException ignored) {
                    // a game that no longer exists
                }
            }
        }
        return out;
    }

    private static int passedCount(Map<Topic, Map<Integer, int[]>> p) {
        int n = 0;
        for (Map<Integer, int[]> m : p.values()) for (int[] v : m.values()) if (v[1] == 1) n++;
        return n;
    }

    /**
     * The child's sea: every open island, then the first locked one (there is always one more waiting).
     * Generated islands are listed as far as they are open - the list itself is endless.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> sea(Long profileId) {
        Grade grade = profiles.findById(profileId).map(LearnerProfile::getGrade).orElse(null);
        List<Isle> mine = chain(grade);
        List<LearnerStageProgress> base = stages.findByProfileId(profileId);
        List<LearnIsleProgress> gen = isleProgress.findByProfileId(profileId);
        List<Map<String, Object>> isles = new ArrayList<>();
        boolean open = true;
        for (int i = 0; ; i++) {
            Isle isle = i < mine.size() ? mine.get(i) : generated(i - mine.size() + 1, grade);
            Map<Topic, Map<Integer, int[]>> p = progressOf(isle, base, gen);
            int passed = passedCount(p);
            isles.add(view(isle, p, passed, open));
            if (!open) break; // the first locked island ends the list
            open = passed >= needed(isle);
            if (i > 10_000) break; // safety
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("isles", isles);
        out.put("stagesPerGame", STAGES);
        return out;
    }

    private static Map<String, Object> view(Isle isle, Map<Topic, Map<Integer, int[]>> p, int passed, boolean open) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", isle.key());
        m.put("name", isle.name());
        m.put("mark", isle.mark());
        m.put("color", isle.color());
        m.put("tier", isle.tier());
        m.put("generated", isle.tier() > 0);
        m.put("open", open);
        m.put("passed", passed);
        m.put("total", isle.games().size() * STAGES);
        m.put("needed", needed(isle));
        int stars = 0;
        List<Map<String, Object>> games = new ArrayList<>();
        for (Topic t : isle.games()) {
            Map<Integer, int[]> s = p.get(t);
            List<Map<String, Object>> st = new ArrayList<>();
            boolean prevPassed = true;
            for (int k = 1; k <= STAGES; k++) {
                int[] v = s.get(k);
                Map<String, Object> sm = new LinkedHashMap<>();
                sm.put("stage", k);
                sm.put("stars", v == null ? 0 : v[0]);
                sm.put("status", v != null && v[1] == 1 ? "COMPLETED" : open && prevPassed ? "UNLOCKED" : "LOCKED");
                st.add(sm);
                stars += v == null ? 0 : v[0];
                prevPassed = v != null && v[1] == 1;
            }
            Map<String, Object> g = new LinkedHashMap<>();
            g.put("topic", t.name());
            g.put("stages", st);
            games.add(g);
        }
        m.put("stars", stars);
        m.put("games", games);
        return m;
    }

    /** A finished round on a generated island: the stage's result + every answer, all kept. */
    @Transactional
    public Map<String, Object> submit(Long profileId, String isleKey, String rawTopic, int stage, List<AnswerDto> list, long durationMs) {
        LearnerProfile owner = profiles.findById(profileId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        Isle isle = byKey(isleKey, owner.getGrade());
        if (isle == null || isle.tier() == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "אין אי כזה");
        Topic topic;
        try {
            topic = Topic.valueOf(String.valueOf(rawTopic).toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "משחק לא מוכר");
        }
        if (!isle.games().contains(topic)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "המשחק הזה לא באי הזה");
        if (stage < 1 || stage > STAGES) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "שלב לא קיים");
        if (list == null || list.isEmpty() || list.size() > 40) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "אין תשובות");

        // The island must be open and the stage reachable - checked here, not only on the screen.
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sea = (List<Map<String, Object>>) sea(profileId).get("isles");
        Map<String, Object> mine = sea.stream().filter(m -> isleKey.equals(m.get("key"))).findFirst().orElse(null);
        if (mine == null || !Boolean.TRUE.equals(mine.get("open"))) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "האי עוד נעול");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> games = (List<Map<String, Object>>) mine.get("games");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> st = (List<Map<String, Object>>) games.stream().filter(g -> topic.name().equals(g.get("topic"))).findFirst().orElseThrow().get("stages");
        if ("LOCKED".equals(st.get(stage - 1).get("status"))) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "השלב עוד נעול");

        LearnerProfile profile = profiles.findById(profileId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        int total = list.size();
        int right = (int) list.stream().filter(AnswerDto::isCorrect).count();
        int pct = (int) Math.round(right * 100.0 / total);
        int stars = pct >= 90 ? 3 : pct >= 75 ? 2 : pct >= 60 ? 1 : 0;
        boolean passed = pct >= 60;
        int points = right * 10 * (1 + Math.min(isle.tier(), 9) / 3); // higher islands are worth more
        int hints = (int) list.stream().filter(AnswerDto::isHintUsed).count();

        LearnIsleProgress row = isleProgress.findByProfileIdAndIsleKeyAndTopicAndStageNumber(profileId, isleKey, topic.name(), stage)
                .orElseGet(() -> new LearnIsleProgress(profileId, isleKey, topic.name(), stage));
        row.recordAttempt(pct, stars, passed);
        isleProgress.save(row);

        profile.addPoints(points);
        profile.touch();
        profiles.save(profile);
        sessions.save(new LearnerSession(profileId, topic, profile.getGrade(), stage, SessionMode.STAGE, total, right, points, stars, hints, durationMs));
        List<LearnerAnswer> rows = new ArrayList<>(total);
        for (AnswerDto a : list) {
            rows.add(new LearnerAnswer(profileId, topic, Skills.of(a.getSkill(), topic, stage, a.getQuestionText()), stage,
                    SessionMode.STAGE, a.isCorrect(), a.isHintUsed(), a.getQuestionText(), a.getChosenAnswer(), a.getCorrectAnswer()));
        }
        answers.saveAll(rows);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("scorePct", pct);
        out.put("stars", stars);
        out.put("passed", passed);
        out.put("points", points);
        // Did this round open the next island?
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> after = (List<Map<String, Object>>) sea(profileId).get("isles");
        out.put("newIsleOpened", after.stream().filter(m -> Boolean.TRUE.equals(m.get("open"))).count()
                > sea.stream().filter(m -> Boolean.TRUE.equals(m.get("open"))).count());
        return out;
    }
}
