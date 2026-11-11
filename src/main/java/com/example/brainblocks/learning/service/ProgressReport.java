package com.example.brainblocks.learning.service;

import com.example.brainblocks.learning.model.LearnerAnswer;
import com.example.brainblocks.learning.model.LearnerMistake;
import com.example.brainblocks.learning.model.LearnerProfile;
import com.example.brainblocks.learning.model.LearnerSession;
import com.example.brainblocks.learning.model.LearnerStageProgress;
import com.example.brainblocks.learning.model.SessionMode;
import com.example.brainblocks.learning.model.StageStatus;
import com.example.brainblocks.learning.model.Topic;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 2026-11-11 (Shir: "the children's results - what she finds hard and what she doesn't - really
 * organised"): one child's report, computed in code (no AI) from what is saved. Pure: no database,
 * tested with made-up rows (ProgressReportTest).
 *
 * Levels by the share of right answers in the RECENT answers (the last [RECENT] per skill/topic - a
 * child who got better is not "hard" forever): STRONG >= 85%, OK 60-84%, HARD < 60%, and only from
 * [MIN_ANSWERS] answers on - fewer is NEW ("עוד מעט נדע"). Answers with "לא הבנתי" count as answered.
 * Topics answered before per-answer saving existed (2026-11-11) fall back to their rounds' scores.
 */
public final class ProgressReport {

    private ProgressReport() {}

    static final int RECENT = 20;
    static final int MIN_ANSWERS = 4;
    static final int STRONG_PCT = 85;
    static final int OK_PCT = 60;
    static final int INACTIVE_DAYS = 7;

    public static String level(int answered, int correct) {
        if (answered < MIN_ANSWERS) return answered == 0 ? "NONE" : "NEW";
        int pct = pct(correct, answered);
        return pct >= STRONG_PCT ? "STRONG" : pct >= OK_PCT ? "OK" : "HARD";
    }

    static int pct(int correct, int total) {
        return total == 0 ? 0 : (int) Math.round(correct * 100.0 / total);
    }

    /** Island and stage play only - the 60-second race is about speed, not about what is hard. */
    private static boolean counts(LearnerAnswer a) {
        return a.getMode() != SessionMode.TIMED;
    }

    public static Map<String, Object> build(LearnerProfile p, List<LearnerSession> sessions, List<LearnerAnswer> answers,
                                            List<LearnerStageProgress> stages, List<LearnerMistake> mistakes,
                                            LocalDateTime now) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("child", child(p, now));

        List<LearnerAnswer> learning = answers.stream().filter(ProgressReport::counts)
                .sorted(Comparator.comparing(LearnerAnswer::getCreatedAt)).toList();

        // ---- this week ----
        LocalDateTime weekAgo = now.minusDays(7);
        List<LearnerSession> week = sessions.stream().filter(s -> s.getCreatedAt() != null && s.getCreatedAt().isAfter(weekAgo)).toList();
        Map<String, Object> w = new LinkedHashMap<>();
        w.put("minutes", (int) Math.round(week.stream().mapToLong(LearnerSession::getDurationMs).sum() / 60000.0));
        w.put("rounds", week.size());
        int wTotal = week.stream().mapToInt(LearnerSession::getTotalQuestions).sum();
        int wRight = week.stream().mapToInt(LearnerSession::getCorrectCount).sum();
        w.put("answers", wTotal);
        w.put("pct", pct(wRight, wTotal));
        w.put("activeDays", (int) week.stream().map(s -> s.getCreatedAt().toLocalDate()).distinct().count());
        r.put("week", w);

        // ---- stages ----
        int completed = (int) stages.stream().filter(s -> s.getStatus() == StageStatus.COMPLETED).count();
        int stars = stages.stream().mapToInt(LearnerStageProgress::getStarsEarned).sum();
        r.put("stagesCompleted", completed);
        r.put("stars", stars);

        // ---- topics ----
        Map<Topic, List<LearnerAnswer>> byTopic = learning.stream()
                .collect(Collectors.groupingBy(LearnerAnswer::getTopic, LinkedHashMap::new, Collectors.toList()));
        Map<Topic, List<LearnerSession>> sessionsByTopic = sessions.stream()
                .filter(s -> s.getMode() == SessionMode.STAGE)
                .collect(Collectors.groupingBy(LearnerSession::getTopic));
        Map<Topic, List<LearnerStageProgress>> stagesByTopic = stages.stream()
                .collect(Collectors.groupingBy(LearnerStageProgress::getTopic));
        List<Topic> topicsSeen = new ArrayList<>();
        for (Topic t : Topic.values()) {
            if (byTopic.containsKey(t) || sessionsByTopic.containsKey(t) || stagesByTopic.containsKey(t)) topicsSeen.add(t);
        }
        List<Map<String, Object>> topics = new ArrayList<>();
        for (Topic t : topicsSeen) {
            List<LearnerAnswer> list = byTopic.getOrDefault(t, List.of());
            int answered;
            int right;
            if (!list.isEmpty()) {
                List<LearnerAnswer> recent = last(list, RECENT * 2);
                answered = recent.size();
                right = (int) recent.stream().filter(LearnerAnswer::isCorrect).count();
            } else { // older play: only round scores exist
                List<LearnerSession> ss = sessionsByTopic.getOrDefault(t, List.of());
                answered = ss.stream().mapToInt(LearnerSession::getTotalQuestions).sum();
                right = ss.stream().mapToInt(LearnerSession::getCorrectCount).sum();
            }
            List<LearnerStageProgress> st = stagesByTopic.getOrDefault(t, List.of());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("topic", t.name());
            m.put("answered", answered);
            m.put("pct", pct(right, answered));
            m.put("level", level(answered, right));
            m.put("stagesCompleted", (int) st.stream().filter(s -> s.getStatus() == StageStatus.COMPLETED).count());
            m.put("stars", st.stream().mapToInt(LearnerStageProgress::getStarsEarned).sum());
            m.put("highestStage", st.stream().filter(s -> s.getStatus() != StageStatus.LOCKED)
                    .mapToInt(LearnerStageProgress::getStageNumber).max().orElse(answered > 0 ? 1 : 0));
            topics.add(m);
        }
        r.put("topics", topics);

        // ---- skills ----
        Map<String, List<LearnerAnswer>> bySkill = learning.stream()
                .collect(Collectors.groupingBy(LearnerAnswer::getSkill, LinkedHashMap::new, Collectors.toList()));
        List<Map<String, Object>> skills = new ArrayList<>();
        for (Map.Entry<String, List<LearnerAnswer>> e : bySkill.entrySet()) {
            if ("OTHER".equals(e.getKey())) continue;
            List<LearnerAnswer> recent = last(e.getValue(), RECENT);
            int answered = recent.size();
            int right = (int) recent.stream().filter(LearnerAnswer::isCorrect).count();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("skill", e.getKey());
            m.put("name", Skills.name(e.getKey()));
            m.put("topic", e.getValue().get(e.getValue().size() - 1).getTopic().name());
            m.put("answered", answered);
            m.put("correct", right);
            m.put("pct", pct(right, answered));
            m.put("level", level(answered, right));
            m.put("hints", (int) recent.stream().filter(LearnerAnswer::isHintUsed).count());
            m.put("stage", mostCommonStage(recent));
            // The newest wrong answer - the teacher sees what the mistake looks like.
            recent.stream().filter(a -> !a.isCorrect()).reduce((a, b) -> b).ifPresent(a -> {
                Map<String, Object> ex = new LinkedHashMap<>();
                ex.put("question", a.getQuestionText());
                ex.put("given", a.getGivenAnswer());
                ex.put("correct", a.getCorrectAnswer());
                m.put("example", ex);
            });
            skills.add(m);
        }
        r.put("skills", skills);
        List<Map<String, Object>> hard = skills.stream().filter(m -> "HARD".equals(m.get("level")))
                .sorted(Comparator.comparingInt(m -> (int) m.get("pct"))).limit(3).toList();
        List<Map<String, Object>> strong = skills.stream().filter(m -> "STRONG".equals(m.get("level")))
                .sorted(Comparator.comparingInt((Map<String, Object> m) -> (int) m.get("pct")).reversed()
                        .thenComparing(Comparator.comparingInt((Map<String, Object> m) -> (int) m.get("answered")).reversed()))
                .limit(3).toList();
        r.put("hard", hard);
        r.put("strong", strong);

        // ---- trend: the last 4 weeks (oldest first), Sunday to Saturday ----
        LocalDate thisSunday = now.toLocalDate().with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
        List<Map<String, Object>> trend = new ArrayList<>();
        for (int i = 3; i >= 0; i--) {
            LocalDate from = thisSunday.minusWeeks(i);
            LocalDate to = from.plusWeeks(1);
            List<LearnerAnswer> inWeek = learning.stream().filter(a -> !a.getCreatedAt().toLocalDate().isBefore(from)
                    && a.getCreatedAt().toLocalDate().isBefore(to)).toList();
            int answered = inWeek.size();
            int right = (int) inWeek.stream().filter(LearnerAnswer::isCorrect).count();
            if (answered == 0) { // before per-answer saving: round scores of that week
                List<LearnerSession> ss = sessions.stream().filter(s -> s.getMode() != SessionMode.TIMED
                        && !s.getCreatedAt().toLocalDate().isBefore(from) && s.getCreatedAt().toLocalDate().isBefore(to)).toList();
                answered = ss.stream().mapToInt(LearnerSession::getTotalQuestions).sum();
                right = ss.stream().mapToInt(LearnerSession::getCorrectCount).sum();
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("weekStart", from.toString());
            m.put("answered", answered);
            m.put("pct", answered == 0 ? null : pct(right, answered));
            trend.add(m);
        }
        r.put("trend", trend);
        r.put("trendDirection", direction(trend));

        // ---- needs attention ----
        List<String> attention = new ArrayList<>();
        if (p.getLastActiveAt() != null && Duration.between(p.getLastActiveAt(), now).toDays() >= INACTIVE_DAYS) {
            attention.add("לא נכנס/ה " + Duration.between(p.getLastActiveAt(), now).toDays() + " ימים");
        }
        if ("DOWN".equals(r.get("trendDirection"))) attention.add("ירידה בהצלחה בשבוע האחרון");
        List<LearnerSession> stageRounds = sessions.stream().filter(s -> s.getMode() == SessionMode.STAGE)
                .sorted(Comparator.comparing(LearnerSession::getCreatedAt)).toList();
        if (stageRounds.size() >= 3 && last(stageRounds, 3).stream().allMatch(s -> s.getStars() == 0)) {
            attention.add("3 סבבים ברצף בלי לעבור");
        }
        if (!hard.isEmpty()) attention.add("מתקשה ב" + hard.get(0).get("name"));
        r.put("attention", attention);

        r.put("openMistakes", (int) mistakes.stream().filter(m -> !m.isResolved()).count());
        r.put("recommendation", recommendation(hard, topics));
        return r;
    }

    private static Map<String, Object> child(LearnerProfile p, LocalDateTime now) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("id", p.getId());
        c.put("name", p.getName());
        c.put("avatar", p.getAvatar());
        c.put("grade", p.getGrade());
        c.put("username", p.getUsername());
        c.put("totalPoints", p.getTotalPoints());
        LocalDate today = now.toLocalDate();
        boolean streakAlive = today.equals(p.getLastDailyDate()) || today.minusDays(1).equals(p.getLastDailyDate());
        c.put("dailyStreak", streakAlive ? p.getDailyStreak() : 0);
        c.put("lastActiveAt", p.getLastActiveAt());
        c.put("createdAt", p.getCreatedAt());
        return c;
    }

    /** UP / DOWN / STEADY: the last week with answers against the one before it (10 points = a change). */
    static String direction(List<Map<String, Object>> trend) {
        List<Integer> pcts = trend.stream().map(m -> (Integer) m.get("pct")).filter(java.util.Objects::nonNull).toList();
        if (pcts.size() < 2) return "UNKNOWN";
        int d = pcts.get(pcts.size() - 1) - pcts.get(pcts.size() - 2);
        return d >= 10 ? "UP" : d <= -10 ? "DOWN" : "STEADY";
    }

    private static Map<String, Object> recommendation(List<Map<String, Object>> hard, List<Map<String, Object>> topics) {
        Map<String, Object> rec = new LinkedHashMap<>();
        if (!hard.isEmpty()) {
            Map<String, Object> h = hard.get(0);
            rec.put("kind", "PRACTICE");
            rec.put("topic", h.get("topic"));
            rec.put("stage", h.get("stage"));
            rec.put("text", "כדאי לתרגל: " + h.get("name") + " (שלב " + h.get("stage") + ")");
            return rec;
        }
        // Nothing is hard: go on where the child is.
        return topics.stream().filter(t -> (int) t.get("stagesCompleted") < 6 && (int) t.get("answered") > 0)
                .max(Comparator.comparingInt(t -> (int) t.get("stagesCompleted")))
                .map(t -> {
                    rec.put("kind", "CONTINUE");
                    rec.put("topic", t.get("topic"));
                    rec.put("stage", Math.min(6, (int) t.get("stagesCompleted") + 1));
                    rec.put("text", "אפשר להמשיך לשלב " + Math.min(6, (int) t.get("stagesCompleted") + 1));
                    return rec;
                })
                .orElseGet(() -> {
                    rec.put("kind", "START");
                    rec.put("text", "עוד לא התחיל/ה לשחק");
                    return rec;
                });
    }

    private static int mostCommonStage(List<LearnerAnswer> list) {
        return list.stream().collect(Collectors.groupingBy(LearnerAnswer::getStageNumber, Collectors.counting()))
                .entrySet().stream().max(Map.Entry.<Integer, Long>comparingByValue().thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey).orElse(1);
    }

    private static <T> List<T> last(List<T> list, int n) {
        return list.size() <= n ? list : list.subList(list.size() - n, list.size());
    }

    /** One row of the class table: per-topic level + the attention notes. */
    public static Map<String, Object> summary(Map<String, Object> report) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("child", report.get("child"));
        s.put("week", report.get("week"));
        s.put("stars", report.get("stars"));
        s.put("stagesCompleted", report.get("stagesCompleted"));
        Map<String, Object> levels = new LinkedHashMap<>();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> topics = (List<Map<String, Object>>) report.get("topics");
        for (Map<String, Object> t : topics) {
            levels.put((String) t.get("topic"), Map.of("level", t.get("level"), "pct", t.get("pct")));
        }
        s.put("topics", levels);
        s.put("hard", report.get("hard"));
        s.put("attention", report.get("attention"));
        s.put("trendDirection", report.get("trendDirection"));
        return s;
    }
}
