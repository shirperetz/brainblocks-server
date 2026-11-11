package com.example.brainblocks.learning.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.brainblocks.learning.ai.Tutor;
import com.example.brainblocks.learning.dto.response.QuestionResponse;
import com.example.brainblocks.learning.model.Grade;
import com.example.brainblocks.learning.model.Topic;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * 2026-11-11: geometry in three tracks (זוויות / צלעות והיקף / שטח) and "השבר כמנת חילוק" (her grade-5
 * workbook) - every question solved here independently, every option distinct, help for every kind.
 */
class GeometryTracksAndDivisionTest {

    private static final Pattern NUM = Pattern.compile("\\d+");

    @Test
    void everyTrackQuestionIsRightAndBelongsToItsTrack() {
        for (Topic track : List.of(Topic.ANGLES, Topic.PERIMETER, Topic.AREA)) {
            for (long seed = 0; seed < 800; seed++) {
                FractionGeometryQuestions gen = new FractionGeometryQuestions(new Random(seed));
                for (int stage = 1; stage <= 6; stage++) {
                    QuestionResponse q = gen.geometryTrack(0, track, stage, true);
                    String t = q.getText();
                    assertThat(q.getTopic()).as(t).isEqualTo(track);
                    assertThat(new HashSet<>(q.getOptions())).as(t).hasSameSizeAs(q.getOptions());
                    assertThat(Tutor.explain(t, 0)).as("help for " + t).isNotNull();
                    assertThat(Tutor.explain(t, 1)).as("help for " + t).isNotNull();
                    String correct = q.getOptions().get(q.getCorrectIndex());
                    List<Integer> n = NUM.matcher(t).results().map(r -> Integer.parseInt(r.group())).toList();
                    if (t.startsWith("זווית של")) {
                        int a = n.get(0);
                        String expected = a < 90 ? "חדה" : a == 90 ? "ישרה" : a < 180 ? "קהה" : "שטוחה";
                        assertThat(correct).as(t).isEqualTo(expected);
                        continue;
                    }
                    if (t.startsWith("איך קוראים לצורה")) {
                        assertThat(track).isEqualTo(Topic.PERIMETER);
                        continue;
                    }
                    int answer = Integer.parseInt(correct);
                    assertThat(answer).as(t).isPositive();
                    if (t.contains("סביב נקודה")) {
                        assertThat(answer).as(t).isEqualTo(360 - n.get(0) - n.get(1));
                    } else if (t.contains("קו ישר")) {
                        assertThat(answer).as(t).isEqualTo(180 - n.get(0));
                    } else if (t.contains("החדה")) {
                        assertThat(answer).as(t).isEqualTo(90 - n.get(0));
                    } else if (t.startsWith("במשולש יש זווית")) {
                        assertThat(answer).as(t).isEqualTo(180 - n.get(0) - n.get(1));
                    } else if (t.contains("ההיקף של מלבן") && t.contains("מה השטח")) {
                        int width = n.get(0) / 2 - n.get(1);
                        assertThat(width).as(t).isPositive();
                        assertThat(answer).as(t).isEqualTo(n.get(1) * width);
                    } else if (t.contains("הרוחב")) {
                        assertThat(2 * (n.get(1) + answer)).as(t).isEqualTo(n.get(0));
                    } else if (t.contains("אורך הצלע")) {
                        assertThat(answer * answer).as(t).isEqualTo(n.get(0));
                    } else if (t.startsWith("למשולש יש צלעות")) {
                        assertThat(answer).as(t).isEqualTo(n.get(0) + n.get(1) + n.get(2));
                    } else if (t.contains("כמה צלעות")) {
                        assertThat(answer).isEqualTo(q.getVisual().getA());
                    } else if (t.contains("משולש")) {
                        assertThat(answer * 2).as(t).isEqualTo(n.get(0) * n.get(1));
                    } else if (t.contains("ריבוע")) {
                        assertThat(answer).as(t).isEqualTo(t.contains("שטח") ? n.get(0) * n.get(0) : 4 * n.get(0));
                    } else if (t.contains("היקף")) {
                        assertThat(answer).as(t).isEqualTo(2 * (n.get(0) + n.get(1)));
                    } else {
                        assertThat(answer).as(t).isEqualTo(n.get(0) * n.get(1));
                    }
                    // the track's own subject only
                    if (track == Topic.ANGLES) assertThat(t).as(t).containsAnyOf("זווי", "°");
                    if (track == Topic.AREA) assertThat(t).as(t).containsAnyOf("שטח");
                    if (track == Topic.PERIMETER) assertThat(t).as(t).doesNotContain("שטח").doesNotContain("°");
                }
            }
        }
    }

    @Test
    void fractionAsDivisionLevelIsRight() {
        Pattern div = Pattern.compile("(\\d+) : (\\d+)");
        Pattern frac = Pattern.compile("(\\d+)/(\\d+)");
        int seen = 0;
        for (long seed = 0; seed < 1500; seed++) {
            FractionGeometryQuestions gen = new FractionGeometryQuestions(new Random(seed));
            for (Grade g : List.of(Grade.G5, Grade.G6)) {
                QuestionResponse q = gen.fraction(0, g, 2, true);
                String t = q.getText();
                String correct = q.getOptions().get(q.getCorrectIndex());
                assertThat(new HashSet<>(q.getOptions())).as(t).hasSameSizeAs(q.getOptions());
                assertThat(Tutor.explain(t, 0)).as("help for " + t).isNotNull();
                if (t.startsWith("כתבו את התרגיל")) {
                    Matcher m = div.matcher(t);
                    m.find();
                    assertThat(correct).as(t).isEqualTo(m.group(1) + "/" + m.group(2));
                    seen++;
                } else if (t.startsWith("כתבו את השבר")) {
                    Matcher m = frac.matcher(t);
                    m.find();
                    assertThat(correct).as(t).isEqualTo(m.group(1) + " : " + m.group(2));
                    seen++;
                } else if (t.startsWith("התוצאה של")) {
                    Matcher m = div.matcher(t);
                    m.find();
                    int a = Integer.parseInt(m.group(1)), b = Integer.parseInt(m.group(2));
                    assertThat(correct).as(t).isEqualTo(a < b ? "קטנה מ-1" : a == b ? "שווה ל-1" : "גדולה מ-1");
                    assertThat(q.getOptions()).hasSize(3);
                    seen++;
                }
            }
        }
        assertThat(seen).as("the new kinds are most of level 2").isGreaterThan(1500);
    }

    /** 2026-11-11 (Shir): writing fractions and wholes-with-remainder, each its own level; no "biggest". */
    @Test
    void writingFractionsAndWholesWithRemainder() {
        Pattern frac = Pattern.compile("(\\d+)/(\\d+)");
        int write = 0, wholes = 0;
        for (long seed = 0; seed < 1500; seed++) {
            FractionGeometryQuestions gen = new FractionGeometryQuestions(new Random(seed));
            for (Grade g : List.of(Grade.G5, Grade.G6)) {
                for (int stage = 1; stage <= 6; stage++) {
                    QuestionResponse q = gen.fraction(0, g, stage, true);
                    String t = q.getText();
                    assertThat(t).as("no 'which is biggest' any more").doesNotContain("הכי גדול");
                    String correct = q.getOptions().get(q.getCorrectIndex());
                    if (t.contains("עשרוני")) continue; // decimals (grade 6) - FractionGeometryQuestionsTest
                    if (t.startsWith("איך כותבים") || t.startsWith("איך קוראים לשבר") || t.startsWith("כמה שלמים")) {
                        assertThat(q.getOptions()).as(t).hasSize(4);
                        assertThat(new HashSet<>(q.getOptions())).as(t + " " + q.getOptions()).hasSize(4);
                        assertThat(Tutor.explain(t, 0)).as("help " + t).isNotNull();
                        assertThat(Tutor.explain(t, 0).text()).as("help never tells " + t).doesNotContain(correct);
                    }
                    if (t.startsWith("איך כותבים")) {
                        Matcher m = frac.matcher(correct);
                        assertThat(m.matches()).as(t).isTrue();
                        int n = Integer.parseInt(m.group(1)), d = Integer.parseInt(m.group(2));
                        assertThat(t).as(t).contains(FractionGeometryQuestions.fractionWords(n, d) + "?");
                        write++;
                    } else if (t.startsWith("איך קוראים לשבר")) {
                        Matcher m = frac.matcher(t);
                        m.find();
                        assertThat(correct).as(t).isEqualTo(FractionGeometryQuestions.fractionWords(
                                Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))));
                        write++;
                    } else if (t.startsWith("כמה שלמים הם")) {
                        Matcher m = frac.matcher(t);
                        m.find();
                        int n = Integer.parseInt(m.group(1)), d = Integer.parseInt(m.group(2));
                        assertThat(n % d).isZero();
                        assertThat(Integer.parseInt(correct)).as(t).isEqualTo(n / d);
                        wholes++;
                    } else if (t.startsWith("כמה שלמים יש ב")) {
                        Matcher m = frac.matcher(t);
                        m.find();
                        int n = Integer.parseInt(m.group(1)), d = Integer.parseInt(m.group(2));
                        int w = correct.startsWith("שלם אחד") ? 1 : Integer.parseInt(correct.split(" ")[0]);
                        int r = Integer.parseInt(correct.replaceAll(".*ושארית ", ""));
                        assertThat(w * d + r).as(t + " -> " + correct).isEqualTo(n);
                        assertThat(r).as(t).isBetween(1, d - 1);
                        wholes++;
                    }
                }
            }
        }
        assertThat(write).isGreaterThan(1000);
        assertThat(wholes).isGreaterThan(1000);
    }

    @Test
    void fractionWordsAreHebrew() {
        assertThat(FractionGeometryQuestions.fractionWords(3, 5)).isEqualTo("שלוש חמישיות");
        assertThat(FractionGeometryQuestions.fractionWords(2, 3)).isEqualTo("שני שלישים");
        assertThat(FractionGeometryQuestions.fractionWords(3, 4)).isEqualTo("שלושה רבעים");
        assertThat(FractionGeometryQuestions.fractionWords(1, 8)).isEqualTo("שמינית");
        assertThat(FractionGeometryQuestions.fractionWords(7, 10)).isEqualTo("שבע עשיריות");
    }
}
