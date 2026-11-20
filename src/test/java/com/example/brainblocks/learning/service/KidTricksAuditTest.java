package com.example.brainblocks.learning.service;

import com.example.brainblocks.learning.dto.response.QuestionResponse;
import com.example.brainblocks.learning.model.Difficulty;
import com.example.brainblocks.learning.model.Grade;
import com.example.brainblocks.learning.model.Topic;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 2026-11-11 (Shir: "be a child, try to outsmart it, so children really have to calculate").
 * A "sneaky child" answers every question WITHOUT solving it, with the tricks children use:
 * the biggest / smallest option, the one in the middle, only the last digit, a rough estimate,
 * copying the numbers of the question, the odd one out. Guessing is 1 in 4 (25%); a trick that
 * scores well above that means the wrong answers give the right one away.
 * <p>
 * Scores are exact expectations (a tie between k options counts 1/k), over many questions per
 * topic, grade and level. {@link #report()} prints the table; {@link #noTrickBeatsGuessingByMuch()}
 * is the bar the generators must keep.
 */
class KidTricksAuditTest {

    static final double BAR = 0.42; // on average per topic, a trick may not get more than 42% (guessing: 25%)

    private static final Pattern OP = Pattern.compile("(\\d+) ([+\\-×÷]) (\\d+)");
    private static final Pattern NUM = Pattern.compile("\\d+");

    /** A trick: from the question, a score 0..1 for each option (the child picks among the top ones). */
    interface Trick extends Function<QuestionResponse, double[]> {
    }

    static Map<String, Trick> tricks() {
        Map<String, Trick> t = new LinkedHashMap<>();
        t.put("הכי גדול", q -> byNumber(q, x -> x));
        t.put("הכי קטן", q -> byNumber(q, x -> -x));
        t.put("באמצע", q -> {
            List<Double> v = numbers(q);
            if (v == null) return null;
            double mean = v.stream().mapToDouble(d -> d).average().orElse(0);
            return v.stream().mapToDouble(x -> -Math.abs(x - mean)).toArray();
        });
        t.put("רק ספרת האחדות", q -> {
            Integer last = lastDigit(q.getText());
            List<Double> v = numbers(q);
            if (last == null || v == null) return null;
            return v.stream().mapToDouble(x -> ((long) Math.abs(x)) % 10 == last ? 1 : 0).toArray();
        });
        t.put("הערכה בעיגול", q -> {
            Double est = estimate(q.getText());
            List<Double> v = numbers(q);
            if (est == null || v == null) return null;
            return v.stream().mapToDouble(x -> -Math.abs(x - est)).toArray();
        });
        t.put("להעתיק מהשאלה", q -> {
            List<String> inQuestion = NUM.matcher(q.getText()).results().map(java.util.regex.MatchResult::group).toList();
            return q.getOptions().stream().mapToDouble(o -> {
                List<String> mine = NUM.matcher(o).results().map(java.util.regex.MatchResult::group).toList();
                if (mine.isEmpty()) return 0;
                // numbers of the question, in the question's order, score highest
                int hits = 0, last = -1;
                boolean ordered = true;
                for (String n : mine) {
                    int at = inQuestion.indexOf(n);
                    if (at >= 0) hits++;
                    if (at < last) ordered = false;
                    last = Math.max(last, at);
                }
                return hits + (ordered && hits == mine.size() ? 0.5 : 0);
            }).toArray();
        });
        t.put("השונה מכולם", q -> {
            List<String> o = q.getOptions();
            double[] s = new double[o.size()];
            for (Function<String, Object> f : List.<Function<String, Object>>of(
                    x -> x.length(), x -> x.replaceAll("\\D", "").length(),
                    x -> x.isEmpty() ? ' ' : x.charAt(x.length() - 1), KidTricksAuditTest::parity)) {
                for (int i = 0; i < o.size(); i++) {
                    Object k = f.apply(o.get(i));
                    int same = 0;
                    for (String other : o) if (f.apply(other).equals(k)) same++;
                    if (same == 1) s[i] += 1;
                }
            }
            return s;
        });
        return t;
    }

    /** Odd/even of the last digit in the option ("" when there is none). */
    static Object parity(String x) {
        String digits = x.replaceAll("\\D", "");
        return digits.isEmpty() ? "" : (digits.charAt(digits.length() - 1) - '0') % 2;
    }

    /** Exact chance the trick picks the right answer (ties shared). null = the trick can't be used. */
    static Double score(Trick trick, QuestionResponse q) {
        double[] s = trick.apply(q);
        if (s == null) return null;
        double best = Double.NEGATIVE_INFINITY;
        for (double x : s) best = Math.max(best, x);
        int ties = 0;
        for (double x : s) if (x == best) ties++;
        return s[q.getCorrectIndex()] == best ? 1.0 / ties : 0.0;
    }

    private static double[] byNumber(QuestionResponse q, Function<Double, Double> f) {
        List<Double> v = numbers(q);
        return v == null ? null : v.stream().mapToDouble(f::apply).map(x -> x).toArray();
    }

    /** The options as numbers (a fraction as its value, "3 שלמים ושארית 2" as 3.2), or null. */
    static List<Double> numbers(QuestionResponse q) {
        List<Double> v = new ArrayList<>();
        for (String o : q.getOptions()) {
            String x = o.replace("°", "").trim();
            if (x.matches("\\d+")) v.add(Double.parseDouble(x));
            else if (x.matches("\\d+/\\d+")) v.add(Double.parseDouble(x.split("/")[0]) / Math.max(1, Double.parseDouble(x.split("/")[1])));
            else if (x.matches("\\d+\\.\\d+")) v.add(Double.parseDouble(x));
            else return null;
        }
        return v;
    }

    /** The ones digit of "a op b" - all a child needs to compute for this trick. */
    static Integer lastDigit(String text) {
        Matcher m = OP.matcher(text);
        if (!m.find() || text.contains("?" + " " + m.group(2)) || text.contains("= ")) return null;
        // Only a shortcut when the numbers have two digits or more - for 7 + 5 the last digit IS the answer.
        if (Long.parseLong(m.group(1)) < 10 || Long.parseLong(m.group(3)) < 10) return null;
        long a = Long.parseLong(m.group(1)) % 10, b = Long.parseLong(m.group(3)) % 10;
        return switch (m.group(2)) {
            case "+" -> (int) ((a + b) % 10);
            case "-" -> (int) (((a - b) % 10 + 10) % 10);
            case "×" -> (int) ((a * b) % 10);
            default -> null;
        };
    }

    /** "1,284 + 395" -> about 1,300 + 400: one significant figure each. */
    static Double estimate(String text) {
        Matcher m = OP.matcher(text);
        if (!m.find() || text.contains("= ")) return null;
        long ra = Long.parseLong(m.group(1)), rb = Long.parseLong(m.group(3));
        double a = round1(ra), b = round1(rb);
        if (a == ra && b == rb) return null; // nothing was rounded - that's computing, not estimating
        // Estimating 60 ÷ 7 or 20 × 7 is still dividing / multiplying - only + and - have a real shortcut.
        if (!m.group(2).equals("+") && !m.group(2).equals("-")) return null;
        return switch (m.group(2)) {
            case "+" -> a + b;
            case "-" -> a - b;
            case "×" -> a * b;
            default -> b == 0 ? null : a / b;
        };
    }

    private static double round1(long x) {
        if (x < 10) return x;
        double p = Math.pow(10, Math.floor(Math.log10(x)));
        return Math.round(x / p) * p;
    }

    // ---- the run ------------------------------------------------------------------------------

    static final List<Topic> TOPICS = List.of(Topic.ADDITION, Topic.SUBTRACTION, Topic.MULTIPLICATION, Topic.DIVISION,
            Topic.COMPARISON, Topic.SEQUENCES, Topic.WORD_PROBLEMS, Topic.FRACTIONS, Topic.ANGLES, Topic.ANGLE_CALC,
            Topic.PERIMETER, Topic.AREA,
            // 2026-11-21: the new topics
            Topic.EVEN_ODD, Topic.MAKE_TEN, Topic.ORDER_OPS, Topic.ROUNDING, Topic.DIVISIBILITY, Topic.MEASURE,
            Topic.DECIMALS, Topic.PERCENT, Topic.RATIO, Topic.DATA, Topic.CIRCLE, Topic.VOLUME, Topic.EQUATIONS);

    record Row(Topic topic, Grade grade, int stage, String trick, double rate, int n) {
    }

    static List<Row> run(int perCell) {
        LearningQuestionService service = new LearningQuestionService();
        Map<String, Trick> tricks = tricks();
        List<Row> rows = new ArrayList<>();
        for (Topic topic : TOPICS) {
            for (Grade grade : Grade.values()) {
                if ((topic == Topic.FRACTIONS || topic.name().startsWith("ANGLE") || topic == Topic.PERIMETER || topic == Topic.AREA)
                        && grade.ordinal() < Grade.G5.ordinal()) continue;
                if ((topic == Topic.MULTIPLICATION || topic == Topic.DIVISION) && grade.ordinal() < Grade.G3.ordinal()) continue;
                if (ExtraTopicsQuestions.handles(topic) && !GradeTopics.allowed(topic, grade)) continue;
                for (int stage = 1; stage <= 6; stage++) {
                    List<QuestionResponse> qs = new ArrayList<>();
                    for (int i = 0; i < perCell / 10; i++) qs.addAll(service.generate(topic, grade, Difficulty.MEDIUM, stage, 10));
                    for (var e : tricks.entrySet()) {
                        // COMPARISON asks for the biggest/smallest - that IS the skill, not a trick.
                        if (topic == Topic.COMPARISON && (e.getKey().equals("הכי גדול") || e.getKey().equals("הכי קטן"))) continue;
                        // 2026-11-21: "the even one among odd ones" IS knowing even/odd; estimating IS the rounding skill.
                        if (topic == Topic.EVEN_ODD && e.getKey().equals("השונה מכולם")) continue;
                        if (topic == Topic.ROUNDING && e.getKey().equals("הערכה בעיגול")) continue;
                        double sum = 0;
                        int n = 0;
                        for (QuestionResponse q : qs) {
                            Double s = score(e.getValue(), q);
                            if (s != null) {
                                sum += s;
                                n++;
                            }
                        }
                        if (n >= perCell / 4) rows.add(new Row(topic, grade, stage, e.getKey(), sum / n, n));
                    }
                }
            }
        }
        return rows;
    }

    @Test
    void report() throws Exception {
        List<Row> rows = run(400);
        StringBuilder out = new StringBuilder();
        out.append("=== KID TRICKS - worst cells (guessing = 25%) ===\n");
        rows.stream().sorted((a, b) -> Double.compare(b.rate(), a.rate())).limit(60)
                .forEach(r -> out.append(String.format("%5.1f%%  %-14s %s level %d  trick: %s  (n=%d)%n",
                        100 * r.rate(), r.topic(), r.grade(), r.stage(), r.trick(), r.n())));
        Map<String, double[]> byTopicTrick = new LinkedHashMap<>();
        for (Row r : rows) {
            double[] acc = byTopicTrick.computeIfAbsent(r.topic() + " | " + r.trick(), k -> new double[2]);
            acc[0] += r.rate();
            acc[1]++;
        }
        out.append("=== average per topic and trick ===\n");
        byTopicTrick.entrySet().stream().sorted((a, b) -> Double.compare(b.getValue()[0] / b.getValue()[1], a.getValue()[0] / a.getValue()[1]))
                .forEach(e -> out.append(String.format("%5.1f%%  %s%n", 100 * e.getValue()[0] / e.getValue()[1], e.getKey())));
        java.nio.file.Files.writeString(java.nio.file.Path.of("target/kid-tricks.txt"), out, java.nio.charset.StandardCharsets.UTF_8);
    }

    /** The bar for every generator change: no trick, on average over a topic's grades and levels, above 42%. */
    @Test
    void noTrickBeatsGuessingByMuch() {
        Map<String, double[]> acc = new LinkedHashMap<>();
        for (Row r : run(200)) {
            double[] a = acc.computeIfAbsent(r.topic() + " | " + r.trick(), k -> new double[2]);
            a[0] += r.rate();
            a[1]++;
        }
        for (var e : acc.entrySet()) {
            double avg = e.getValue()[0] / e.getValue()[1];
            org.assertj.core.api.Assertions.assertThat(avg).as("a child gets " + Math.round(100 * avg) + "% without calculating: " + e.getKey())
                    .isLessThanOrEqualTo(BAR);
        }
    }
}
