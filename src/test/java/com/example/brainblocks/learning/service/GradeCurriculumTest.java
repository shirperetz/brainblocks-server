package com.example.brainblocks.learning.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.brainblocks.learning.ai.Tutor;
import com.example.brainblocks.learning.dto.response.QuestionResponse;
import com.example.brainblocks.learning.model.Grade;
import com.example.brainblocks.learning.model.Topic;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * 2026-11-12 (Shir: "by grade - what each child learns, at its grade's level; ו׳ learns higher geometry
 * than ה׳"): which topics each grade gets, and that a lower grade gets only its own level of fractions
 * and geometry; ה׳ exactly as before; ו׳ its own harder questions - every one solved here.
 */
class GradeCurriculumTest {

    private static final Pattern NUM = Pattern.compile("\\d+");

    @Test
    void eachGradeHasItsTopics() {
        assertThat(GradeTopics.allowed(Topic.MULTIPLICATION, Grade.G1)).isFalse();
        assertThat(GradeTopics.allowed(Topic.MULTIPLICATION, Grade.G2)).isTrue();
        assertThat(GradeTopics.allowed(Topic.DRAGON, Grade.G2)).isTrue();
        assertThat(GradeTopics.allowed(Topic.FRACTIONS, Grade.G2)).isFalse();
        for (Topic t : List.of(Topic.FRACTIONS, Topic.ANGLES, Topic.PERIMETER)) assertThat(GradeTopics.allowed(t, Grade.G3)).as(t.name()).isTrue();
        assertThat(GradeTopics.allowed(Topic.AREA, Grade.G3)).isFalse();
        assertThat(GradeTopics.allowed(Topic.AREA, Grade.G4)).isTrue();
        assertThat(GradeTopics.allowed(Topic.ANGLE_CALC, Grade.G4)).isFalse();
        assertThat(GradeTopics.allowed(Topic.ANGLE_CALC, Grade.G5)).isTrue();
    }

    private static Set<String> skills(Grade grade, Topic topic) {
        Set<String> out = new HashSet<>();
        for (long seed = 0; seed < 400; seed++) {
            FractionGeometryQuestions gen = new FractionGeometryQuestions(new Random(seed));
            for (int stage = 1; stage <= 6; stage++) {
                QuestionResponse q = topic == Topic.FRACTIONS ? gen.fraction(seed % 10 < 5 ? 0 : 7, grade, stage, true)
                        : gen.geometryTrack(seed % 10 < 5 ? 0 : 7, topic, stage, true, grade);
                out.add(q.getSkill());
                assertThat(new HashSet<>(q.getOptions())).as(q.getText()).hasSameSizeAs(q.getOptions());
                assertThat(Tutor.explain(q.getText(), 0)).as("help for " + q.getText()).isNotNull();
            }
        }
        return out;
    }

    @Test
    void lowerGradesGetTheirLevelOnly() {
        assertThat(skills(Grade.G3, Topic.FRACTIONS)).containsOnly("FRAC_WRITE", "FRAC_SHADED");
        assertThat(skills(Grade.G4, Topic.FRACTIONS)).doesNotContain("FRAC_SIMPLIFY", "FRAC_DECIMALS", "FRAC_DIFFERENT_DENOMINATORS",
                "FRAC_AS_DIVISION", "FRAC_WHOLES_REMAINDER").contains("FRAC_EQUIVALENT", "FRAC_SAME_DENOMINATOR");
        assertThat(skills(Grade.G3, Topic.ANGLES)).containsOnly("ANG_TYPE", "ANG_NAMED_DEGREES");
        assertThat(skills(Grade.G3, Topic.PERIMETER)).containsOnly("GEO_SIDES", "GEO_SHAPE_NAME", "PER_RECTANGLE");
        assertThat(skills(Grade.G4, Topic.AREA)).containsOnly("AREA_RECTANGLE", "AREA_SQUARE", "AREA_SIDE_FROM_AREA");
    }

    @Test
    void gradeFiveIsAsBeforeAndGradeSixIsHigher() {
        Set<String> g5 = skills(Grade.G5, Topic.ANGLE_CALC);
        Set<String> g6 = skills(Grade.G6, Topic.ANGLE_CALC);
        assertThat(g5).doesNotContain("ANG_QUADRILATERAL", "ANG_VERTICAL_ADJACENT");
        assertThat(g6).contains("ANG_QUADRILATERAL", "ANG_VERTICAL_ADJACENT").containsAll(g5);
        assertThat(skills(Grade.G5, Topic.AREA)).doesNotContain("AREA_PARALLELOGRAM", "PER_CIRCLE");
        assertThat(skills(Grade.G6, Topic.AREA)).contains("AREA_PARALLELOGRAM", "PER_CIRCLE");
    }

    @Test
    void gradeSixQuestionsAreRight() {
        int checked = 0;
        for (long seed = 0; seed < 600; seed++) {
            FractionGeometryQuestions gen = new FractionGeometryQuestions(new Random(seed));
            for (Topic topic : List.of(Topic.ANGLE_CALC, Topic.AREA)) {
                QuestionResponse q = gen.geometryTrack(seed % 2 == 0 ? 0 : 7, topic, 6, true, Grade.G6);
                String t = q.getText();
                String correct = q.getOptions().get(q.getCorrectIndex());
                List<Integer> n = NUM.matcher(t).results().map(r -> Integer.parseInt(r.group())).toList();
                if (t.startsWith("במרובע")) {
                    assertThat(Integer.parseInt(correct)).as(t).isEqualTo(360 - n.get(0) - n.get(1) - n.get(2)).isPositive();
                    checked++;
                } else if (t.contains("שני ישרים נחתכים")) {
                    int a = n.get(0);
                    assertThat(Integer.parseInt(correct)).as(t).isEqualTo(t.contains("שמולה") ? a : 180 - a);
                    checked++;
                } else if (t.startsWith("למקבילית")) {
                    assertThat(Integer.parseInt(correct)).as(t).isEqualTo(n.get(0) * n.get(2)); // base x height
                    checked++;
                } else if (t.contains("היקף העיגול")) {
                    assertThat(Double.parseDouble(correct)).as(t).isCloseTo(3.14 * n.get(0), org.assertj.core.data.Offset.offset(0.001));
                    checked++;
                }
            }
        }
        assertThat(checked).isGreaterThan(300);
    }
}
