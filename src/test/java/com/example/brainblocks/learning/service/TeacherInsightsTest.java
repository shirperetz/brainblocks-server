package com.example.brainblocks.learning.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.brainblocks.learning.model.Avatar;
import com.example.brainblocks.learning.model.Grade;
import com.example.brainblocks.learning.model.LearnClass;
import com.example.brainblocks.learning.model.LearnQuestion;
import com.example.brainblocks.learning.model.LearnerAnswer;
import com.example.brainblocks.learning.model.LearnerProfile;
import com.example.brainblocks.learning.model.SessionMode;
import com.example.brainblocks.learning.model.Topic;
import com.example.brainblocks.learning.repository.LearnClassRepository;
import com.example.brainblocks.learning.repository.LearnerAnswerRepository;
import com.example.brainblocks.learning.repository.LearnerProfileRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** 2026-11-21: "what to teach tomorrow", Meitzav readiness, and typed (open) answers. */
@SpringBootTest(properties = {
        "DATABASE_URL=jdbc:h2:mem:insights;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "DATABASE_USER=sa", "DATABASE_PASSWORD=", "JOIN_CODE=4711", "ADMIN_USERNAMES=boss", "PUBLIC_ORIGIN=https://example.org"})
@ActiveProfiles("cloud")
class TeacherInsightsTest {
    @Autowired TeacherInsightsService insights;
    @Autowired LearnerProfileRepository profiles;
    @Autowired LearnerAnswerRepository answers;
    @Autowired LearnClassRepository classes;

    private LearnerProfile kid(String name, Long classId) {
        LearnerProfile p = new LearnerProfile(name, Avatar.values()[0]);
        p.setGrade(Grade.G5);
        p.setClassId(classId);
        return profiles.save(p);
    }

    private void answer(LearnerProfile p, Topic t, String skill, int right, int wrong) {
        for (int i = 0; i < right; i++) answers.save(new LearnerAnswer(p.getId(), t, skill, 3, SessionMode.STAGE, true, false, "38 + 2" + i, "6" + i, "6" + i));
        for (int i = 0; i < wrong; i++) answers.save(new LearnerAnswer(p.getId(), t, skill, 3, SessionMode.STAGE, false, false, "38 + 2" + i + "?", "5" + i, "6" + i));
    }

    @Test
    @SuppressWarnings("unchecked")
    void tomorrowFindsTheHardSkillAndSplitsTheClass() {
        LearnerProfile admin = new LearnerProfile("boss", Avatar.values()[0]);
        admin.setAdmin(true);
        admin = profiles.save(admin);
        LearnClass c = new LearnClass("ה1", admin.getId(), "INS001");
        c.setGrade(Grade.G5);
        c = classes.save(c);
        LearnerProfile a = kid("א", c.getId()), b = kid("ב", c.getId()), d = kid("ד", c.getId()), quiet = kid("שקט", c.getId());
        answer(a, Topic.ADDITION, "ADD_CARRY", 1, 6);   // needs help
        answer(b, Topic.ADDITION, "ADD_CARRY", 5, 2);   // on the way
        answer(d, Topic.ADDITION, "ADD_CARRY", 9, 0);   // ready
        answer(a, Topic.ADDITION, "ADD_NO_CARRY", 10, 0); // easy for everyone - not a focus
        answer(b, Topic.ADDITION, "ADD_NO_CARRY", 10, 0);

        Map<String, Object> t = insights.tomorrow(admin.getId(), c.getId());
        List<Map<String, Object>> focus = (List<Map<String, Object>>) t.get("focus");
        assertThat(focus).extracting(f -> f.get("skill")).containsExactly("ADD_CARRY");
        Map<String, List<Map<String, Object>>> groups = (Map<String, List<Map<String, Object>>>) focus.get(0).get("groups");
        assertThat(groups.get("help")).extracting(k -> k.get("name")).containsExactly("א");
        assertThat(groups.get("way")).extracting(k -> k.get("name")).containsExactly("ב");
        assertThat(groups.get("ready")).extracting(k -> k.get("name")).containsExactly("ד");
        assertThat((List<?>) focus.get(0).get("examples")).isNotEmpty().hasSizeLessThanOrEqualTo(4);
        assertThat((List<Map<String, Object>>) t.get("quiet")).extracting(k -> k.get("name")).containsExactly("שקט");

        // Meitzav: whole numbers practiced (30) is not enough for a score; add fractions (35) -> a score
        Map<String, Object> m = insights.meitzav(admin.getId(), c.getId());
        assertThat(m.get("score")).isNull();
        answer(a, Topic.FRACTIONS, "FRAC_ADD", 4, 8);
        answer(b, Topic.FRACTIONS, "FRAC_ADD", 4, 8);
        m = insights.meitzav(admin.getId(), c.getId());
        assertThat(m.get("score")).isNotNull();
        List<Map<String, Object>> plan = (List<Map<String, Object>>) m.get("plan");
        assertThat(plan.get(0).get("area")).isIn("גיאומטריה ומדידות", "נתונים וגרפים"); // not practiced = first
        assertThat((List<String>) plan.get(0).get("topics")).isNotEmpty();
        assertThat((List<?>) m.get("children")).hasSize(4);
    }

    @Test
    void typedNumbersCountByValue() {
        LearnQuestion q = new LearnQuestion(1L, "ADDITION", 1, "G3", "1000 + 250", List.of("1,250", "1,350"), "1,250", null, null);
        assertThat(q.answer(" 1250 ")).isTrue();
        assertThat(q.getChosenAnswer()).isEqualTo("1,250");
        LearnQuestion d = new LearnQuestion(1L, "DECIMALS", 1, "G5", "1.5 + 2", List.of("3.5", "4.5"), "3.5", null, null);
        assertThat(d.answer("3.50")).isTrue();
        LearnQuestion w = new LearnQuestion(1L, "ADDITION", 1, "G3", "2 + 3", List.of("5", "6"), "5", null, null);
        assertThat(w.answer("6")).isFalse();
        LearnQuestion f = new LearnQuestion(1L, "FRACTIONS", 1, "G5", "1/2 + 1/2", List.of("1", "2"), "1", null, null);
        assertThat(f.answer("abc")).isFalse();
    }
}
