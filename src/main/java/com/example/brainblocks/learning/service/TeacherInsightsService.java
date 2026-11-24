package com.example.brainblocks.learning.service;

import com.example.brainblocks.learning.model.Grade;
import com.example.brainblocks.learning.model.LearnerAnswer;
import com.example.brainblocks.learning.model.LearnerProfile;
import com.example.brainblocks.learning.model.SessionMode;
import com.example.brainblocks.learning.model.Topic;
import com.example.brainblocks.learning.repository.LearnClassRepository;
import com.example.brainblocks.learning.repository.LearnerAnswerRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * 2026-11-21 (Shir chose: "what to teach tomorrow" + "Meitzav readiness per class").
 * Both read only the class's real answers (learn_answer); nothing is stored.
 */
@Service
public class TeacherInsightsService {
    static final int TOMORROW_DAYS = 14;
    static final int MEITZAV_DAYS = 30;

    /** The four Meitzav (grade 5) areas and their weight in the test. */
    public enum Area {
        FRACTIONS("שברים ועשרוניים", 35, Topic.FRACTIONS, Topic.DECIMALS, Topic.PERCENT, Topic.RATIO, Topic.FRAC_EXPAND),
        WHOLE("מספרים טבעיים", 30, Topic.ADDITION, Topic.SUBTRACTION, Topic.MULTIPLICATION, Topic.DIVISION, Topic.ORDER_OPS,
                Topic.ROUNDING, Topic.DIVISIBILITY, Topic.EQUATIONS, Topic.EVEN_ODD, Topic.WORD_PROBLEMS, Topic.COMPARISON, Topic.SEQUENCES),
        GEOMETRY("גיאומטריה ומדידות", 25, Topic.GEOMETRY, Topic.ANGLES, Topic.ANGLE_CALC, Topic.PERIMETER, Topic.AREA,
                Topic.CIRCLE, Topic.VOLUME, Topic.MEASURE),
        DATA("נתונים וגרפים", 10, Topic.DATA);

        final String label;
        final int weight;
        final Set<Topic> topics;

        Area(String label, int weight, Topic... topics) {
            this.label = label;
            this.weight = weight;
            this.topics = Set.of(topics);
        }

        static Area of(Topic t) {
            for (Area a : values()) if (a.topics.contains(t)) return a;
            return null;
        }
    }

    private final AccessService access;
    private final LearnerAnswerRepository answers;
    private final LearnClassRepository classes;

    public TeacherInsightsService(AccessService access, LearnerAnswerRepository answers, LearnClassRepository classes) {
        this.access = access;
        this.answers = answers;
        this.classes = classes;
    }

    private List<LearnerAnswer> recent(LearnerProfile p, int days) {
        LocalDateTime from = LocalDateTime.now().minusDays(days);
        return answers.findByProfileId(p.getId()).stream()
                .filter(a -> a.getCreatedAt() != null && a.getCreatedAt().isAfter(from))
                .filter(a -> a.getTopic() != null && !a.getTopic().isGame() && a.getMode() != SessionMode.TIMED)
                .toList();
    }

    private static int pct(int right, int total) {
        return total == 0 ? 0 : Math.round(100f * right / total);
    }

    private static Map<String, Object> kid(LearnerProfile p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId());
        m.put("name", p.getName());
        return m;
    }

    // ---- 2026-11-22 (Shir: "a weekly report for the teacher") ------------------------------------
    // Who practised this week, who improved or dropped against last week, and what is still hard.
    // The last 7 days against the 7 before; a child's accuracy counts only with 10+ answers in a week.

    static final int WEEK_MIN_ANSWERS = 10;

    public Map<String, Object> weekly(Long viewer, Long classId) {
        List<LearnerProfile> kids = access.visibleChildren(viewer, classId);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime weekAgo = now.minusDays(7);
        LocalDateTime twoWeeks = now.minusDays(14);
        List<Map<String, Object>> practiced = new ArrayList<>();
        List<Map<String, Object>> notPracticed = new ArrayList<>();
        List<Map<String, Object>> improved = new ArrayList<>();
        List<Map<String, Object>> dropped = new ArrayList<>();
        Map<String, int[]> bySkill = new HashMap<>();
        int totalAnswers = 0, totalRight = 0;
        for (LearnerProfile p : kids) {
            int[] thisWeek = new int[2], lastWeek = new int[2];
            for (LearnerAnswer a : answers.findTop600ByProfileIdOrderByIdDesc(p.getId())) {
                if (a.getCreatedAt() == null || a.getCreatedAt().isBefore(twoWeeks)) continue;
                if (a.getTopic() != null && a.getTopic().isGame()) continue;
                boolean recent = a.getCreatedAt().isAfter(weekAgo);
                int[] w = recent ? thisWeek : lastWeek;
                w[0]++;
                if (a.isCorrect()) w[1]++;
                if (recent) {
                    String s = a.getSkill() == null ? String.valueOf(a.getTopic()) : a.getSkill();
                    int[] t = bySkill.computeIfAbsent(s, k -> new int[2]);
                    t[0]++;
                    if (a.isCorrect()) t[1]++;
                }
            }
            totalAnswers += thisWeek[0];
            totalRight += thisWeek[1];
            Map<String, Object> row = kid(p);
            row.put("answers", thisWeek[0]);
            row.put("pct", thisWeek[0] >= WEEK_MIN_ANSWERS ? pct(thisWeek[1], thisWeek[0]) : null);
            if (thisWeek[0] == 0) notPracticed.add(kid(p));
            else practiced.add(row);
            if (thisWeek[0] >= WEEK_MIN_ANSWERS && lastWeek[0] >= WEEK_MIN_ANSWERS) {
                int diff = pct(thisWeek[1], thisWeek[0]) - pct(lastWeek[1], lastWeek[0]);
                Map<String, Object> m = kid(p);
                m.put("from", pct(lastWeek[1], lastWeek[0]));
                m.put("to", pct(thisWeek[1], thisWeek[0]));
                if (diff >= 10) improved.add(m);
                if (diff <= -10) dropped.add(m);
            }
        }
        practiced.sort(Comparator.comparingInt(m -> -(Integer) m.get("answers")));
        List<Map<String, Object>> hard = bySkill.entrySet().stream()
                .filter(e -> e.getValue()[0] >= 8 && pct(e.getValue()[1], e.getValue()[0]) < 75)
                .sorted(Comparator.comparingInt(e -> pct(e.getValue()[1], e.getValue()[0])))
                .limit(3)
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", Skills.name(e.getKey()));
                    m.put("pct", pct(e.getValue()[1], e.getValue()[0]));
                    return m;
                })
                .toList();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("children", kids.size());
        out.put("answers", totalAnswers);
        out.put("pct", pct(totalRight, totalAnswers));
        out.put("practiced", practiced);
        out.put("notPracticed", notPracticed);
        out.put("improved", improved);
        out.put("dropped", dropped);
        out.put("hard", hard);
        return out;
    }

    // ---- "מה ללמד מחר" ---------------------------------------------------------------------------

    public Map<String, Object> tomorrow(Long viewer, Long classId) {
        List<LearnerProfile> kids = access.visibleChildren(viewer, classId);
        // skill -> total/right, and per child
        Map<String, int[]> bySkill = new HashMap<>();
        Map<String, Topic> topicOf = new HashMap<>();
        Map<String, Map<LearnerProfile, int[]>> perKid = new HashMap<>();
        Map<String, List<LearnerAnswer>> wrong = new HashMap<>();
        List<Map<String, Object>> quiet = new ArrayList<>();
        for (LearnerProfile p : kids) {
            List<LearnerAnswer> list = recent(p, TOMORROW_DAYS);
            if (list.isEmpty()) quiet.add(kid(p));
            for (LearnerAnswer a : list) {
                String s = a.getSkill() == null ? a.getTopic().name() : a.getSkill();
                topicOf.putIfAbsent(s, a.getTopic());
                int[] t = bySkill.computeIfAbsent(s, k -> new int[2]);
                t[0]++;
                if (a.isCorrect()) t[1]++;
                int[] k = perKid.computeIfAbsent(s, x -> new LinkedHashMap<>()).computeIfAbsent(p, x -> new int[2]);
                k[0]++;
                if (a.isCorrect()) k[1]++;
                if (!a.isCorrect() && a.getQuestionText() != null) wrong.computeIfAbsent(s, x -> new ArrayList<>()).add(a);
            }
        }
        List<Map<String, Object>> focus = bySkill.entrySet().stream()
                .filter(e -> e.getValue()[0] >= 8 && perKid.get(e.getKey()).size() >= 2 && pct(e.getValue()[1], e.getValue()[0]) < 80)
                .sorted(Comparator.comparingInt(e -> pct(e.getValue()[1], e.getValue()[0])))
                .limit(3)
                .map(e -> focusRow(e.getKey(), topicOf.get(e.getKey()), e.getValue(), perKid.get(e.getKey()), wrong.getOrDefault(e.getKey(), List.of())))
                .toList();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("days", TOMORROW_DAYS);
        out.put("children", kids.size());
        out.put("focus", focus);
        out.put("quiet", quiet);
        return out;
    }

    private Map<String, Object> focusRow(String skill, Topic topic, int[] t, Map<LearnerProfile, int[]> kids, List<LearnerAnswer> wrong) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("skill", skill);
        m.put("name", Skills.name(skill));
        m.put("topic", topic == null ? null : topic.name());
        m.put("pct", pct(t[1], t[0]));
        m.put("answers", t[0]);
        List<Map<String, Object>> help = new ArrayList<>(), way = new ArrayList<>(), ready = new ArrayList<>();
        for (Map.Entry<LearnerProfile, int[]> e : kids.entrySet()) {
            int p = pct(e.getValue()[1], e.getValue()[0]);
            Map<String, Object> k = kid(e.getKey());
            k.put("pct", p);
            if (e.getValue()[0] < 3) continue; // too few to say
            (p < 60 ? help : p < 85 ? way : ready).add(k);
        }
        Comparator<Map<String, Object>> byPct = Comparator.comparingInt(x -> (int) x.get("pct"));
        help.sort(byPct);
        way.sort(byPct);
        ready.sort(byPct.reversed());
        Map<String, Object> groups = new LinkedHashMap<>();
        groups.put("help", help);
        groups.put("way", way);
        groups.put("ready", ready);
        m.put("groups", groups);
        // real exercises the class got wrong - the lesson's material (distinct, newest first)
        Set<String> seen = new LinkedHashSet<>();
        List<Map<String, Object>> examples = new ArrayList<>();
        Map<String, Integer> mistakeKinds = new HashMap<>();
        for (int i = wrong.size() - 1; i >= 0; i--) {
            LearnerAnswer a = wrong.get(i);
            String k = Mistakes.classify(a.getTopic(), a.getQuestionText(), a.getCorrectAnswer(), a.getGivenAnswer());
            if (k != null && Mistakes.known(k)) mistakeKinds.merge(k, 1, Integer::sum);
            if (examples.size() < 4 && seen.add(a.getQuestionText())) {
                Map<String, Object> ex = new LinkedHashMap<>();
                ex.put("text", a.getQuestionText());
                ex.put("right", a.getCorrectAnswer());
                ex.put("given", a.getGivenAnswer());
                examples.add(ex);
            }
        }
        m.put("examples", examples);
        mistakeKinds.entrySet().stream().max(Map.Entry.comparingByValue()).ifPresent(e -> {
            m.put("mistake", Map.of("name", Mistakes.label(e.getKey()), "tip", Mistakes.tip(e.getKey()), "times", e.getValue()));
        });
        return m;
    }

    // ---- מוכנות למיצ"ב ---------------------------------------------------------------------------

    public Map<String, Object> meitzav(Long viewer, Long classId) {
        List<LearnerProfile> kids = access.visibleChildren(viewer, classId);
        Map<Area, int[]> cls = new EnumMap<>(Area.class);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (LearnerProfile p : kids) {
            Map<Area, int[]> mine = new EnumMap<>(Area.class);
            for (LearnerAnswer a : recent(p, MEITZAV_DAYS)) {
                Area area = Area.of(a.getTopic());
                if (area == null) continue;
                for (Map<Area, int[]> m : List.of(mine, cls)) {
                    int[] t = m.computeIfAbsent(area, x -> new int[2]);
                    t[0]++;
                    if (a.isCorrect()) t[1]++;
                }
            }
            Map<String, Object> row = kid(p);
            Map<String, Object> areas = new LinkedHashMap<>();
            for (Area a : Area.values()) {
                int[] t = mine.get(a);
                areas.put(a.name(), t == null || t[0] < 5 ? null : pct(t[1], t[0]));
            }
            row.put("areas", areas);
            Integer score = weighted(mine);
            row.put("score", score);
            row.put("level", level(score));
            rows.add(row);
        }
        rows.sort(Comparator.comparing((Map<String, Object> r) -> r.get("score") == null ? 999 : (Integer) r.get("score")));
        List<Map<String, Object>> areas = new ArrayList<>();
        for (Area a : Area.values()) {
            int[] t = cls.get(a);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", a.name());
            m.put("name", a.label);
            m.put("weight", a.weight);
            m.put("answers", t == null ? 0 : t[0]);
            m.put("pct", t == null || t[0] < 10 ? null : pct(t[1], t[0]));
            areas.add(m);
        }
        Grade g = classId == null || classId == 0L ? null : classes.findById(classId).map(c -> c.getGrade()).orElse(null);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("days", MEITZAV_DAYS);
        out.put("grade", g == null ? null : g.name());
        out.put("score", weighted(cls));
        out.put("level", level(weighted(cls)));
        out.put("areas", areas);
        out.put("children", rows);
        out.put("plan", plan(areas, g));
        return out;
    }

    /** The Meitzav score: the areas' percentages by their weight, over the areas that have answers. */
    static Integer weighted(Map<Area, int[]> m) {
        int w = 0;
        double s = 0;
        for (Area a : Area.values()) {
            int[] t = m.get(a);
            if (t == null || t[0] < 5) continue;
            w += a.weight;
            s += a.weight * (100.0 * t[1] / t[0]);
        }
        return w < 50 ? null : (int) Math.round(s / w); // at least half the test's weight must be practiced
    }

    static String level(Integer score) {
        if (score == null) return "NODATA";
        return score >= 80 ? "READY" : score >= 60 ? "ALMOST" : "REVIEW";
    }

    /** Two weeks: the weakest area first (not practiced = weakest), a Meitzav round twice a week. */
    private List<Map<String, Object>> plan(List<Map<String, Object>> areas, Grade grade) {
        List<Map<String, Object>> order = new ArrayList<>(areas);
        order.sort(Comparator.comparingInt(a -> a.get("pct") == null ? -1 : (Integer) a.get("pct")));
        List<Map<String, Object>> weeks = new ArrayList<>();
        for (int w = 0; w < 2 && w < order.size(); w++) {
            Map<String, Object> a = order.get(w);
            Area area = Area.valueOf((String) a.get("key"));
            List<String> topics = area.topics.stream()
                    .filter(t -> grade == null || GradeTopics.allowed(t, grade))
                    .map(Topic::name).sorted().toList();
            Map<String, Object> week = new LinkedHashMap<>();
            week.put("week", w + 1);
            week.put("area", a.get("name"));
            week.put("pct", a.get("pct"));
            week.put("topics", topics);
            week.put("meitzavRounds", 2);
            weeks.add(week);
        }
        return weeks;
    }

    void requireClass(Long classId) {
        if (classId == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    }
}
