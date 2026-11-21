package com.example.brainblocks;

import com.example.brainblocks.learning.model.LearnQuestion;
import com.example.brainblocks.learning.repository.LearnQuestionRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 2026-11-21: answers in a round must name the child's own bank questions (like the real site). */
final class Banked {
    private Banked() {}

    static List<Map<String, Object>> of(LearnQuestionRepository bank, Object profileId, String topic, List<Map<String, Object>> answers) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> a : answers) {
            String right = String.valueOf(a.get("correctAnswer"));
            LearnQuestion q = bank.save(new LearnQuestion(((Number) profileId).longValue(), topic, 1, "G3",
                    String.valueOf(a.get("questionText")), List.of(right, "x"), right, null, null));
            Map<String, Object> m = new HashMap<>(a);
            m.put("bankId", q.getId());
            out.add(m);
        }
        return out;
    }
}
