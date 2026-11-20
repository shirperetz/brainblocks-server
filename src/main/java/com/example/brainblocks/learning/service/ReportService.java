package com.example.brainblocks.learning.service;

import com.example.brainblocks.learning.model.LearnerProfile;
import com.example.brainblocks.learning.repository.LearnerAnswerRepository;
import com.example.brainblocks.learning.repository.LearnerMistakeRepository;
import com.example.brainblocks.learning.repository.LearnerProfileRepository;
import com.example.brainblocks.learning.repository.LearnerSessionRepository;
import com.example.brainblocks.learning.repository.LearnerStageProgressRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 2026-11-11: the progress screen's data - one child's report and the whole-class table. Who may see
 * which child is decided in ONE place ([visibleChildren] / [requireCanSee]): the admin sees every
 * child; (step 3) a teacher only the children of her classes.
 */
@Service
public class ReportService {

    private final LearnerProfileRepository profiles;
    private final LearnerSessionRepository sessions;
    private final LearnerAnswerRepository answers;
    private final LearnerStageProgressRepository stages;
    private final LearnerMistakeRepository mistakes;
    private final AccessService access;

    public ReportService(LearnerProfileRepository profiles, LearnerSessionRepository sessions,
                         LearnerAnswerRepository answers, LearnerStageProgressRepository stages,
                         LearnerMistakeRepository mistakes, AccessService access) {
        this.profiles = profiles;
        this.sessions = sessions;
        this.answers = answers;
        this.stages = stages;
        this.mistakes = mistakes;
        this.access = access;
    }

    public Map<String, Object> childReport(Long viewer, Long childId) {
        access.requireCanSee(viewer, childId);
        LearnerProfile p = profiles.findById(childId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "אין ילד כזה"));
        return report(p);
    }

    private Map<String, Object> report(LearnerProfile p) {
        var all = answers.findByProfileId(p.getId());
        Map<String, Object> r = new java.util.LinkedHashMap<>(ProgressReport.build(p, sessions.findByProfileId(p.getId()), all,
                stages.findByProfileId(p.getId()), mistakes.findByProfileId(p.getId()), LocalDateTime.now()));
        // 2026-11-21: "⭐ ready for a challenge" topics + what a teacher already opened at a higher grade
        r.put("challengeReady", AdvancedLevels.ready(p, all));
        Map<String, String> adv = new java.util.LinkedHashMap<>();
        p.getAdvancedLevels().forEach((t, g) -> adv.put(t.name(), g.name()));
        r.put("advanced", adv);
        return r;
    }

    /** Every visible child's row + what is hard for several children at once. */
    public Map<String, Object> overview(Long viewer, Long classId) {
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Integer> hardCount = new HashMap<>();
        Map<String, String> hardName = new HashMap<>();
        Map<String, Integer> mistakeCount = new HashMap<>(); // 2026-11-12: children per kind of mistake
        for (LearnerProfile p : access.visibleChildren(viewer, classId)) {
            Map<String, Object> report = report(p);
            Map<String, Object> row = ProgressReport.summary(report);
            row.put("challengeReady", ((List<?>) report.get("challengeReady")).size());
            row.put("classId", p.getClassId());
            rows.add(row);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> skills = (List<Map<String, Object>>) report.get("skills");
            for (Map<String, Object> s : skills) {
                if ("HARD".equals(s.get("level"))) {
                    hardCount.merge((String) s.get("skill"), 1, Integer::sum);
                    hardName.put((String) s.get("skill"), (String) s.get("name"));
                }
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> patterns = (List<Map<String, Object>>) report.get("patterns");
            patterns.stream().map(m -> (String) m.get("type")).distinct()
                    .forEach(t -> mistakeCount.merge(t, 1, Integer::sum));
        }
        // Children who need attention first, then by name.
        rows.sort(Comparator.comparing((Map<String, Object> r) -> ((List<?>) r.get("attention")).isEmpty())
                .thenComparing(r -> String.valueOf(((Map<?, ?>) r.get("child")).get("name"))));
        List<Map<String, Object>> common = hardCount.entrySet().stream()
                .filter(e -> e.getValue() >= 2)
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5)
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("skill", e.getKey());
                    m.put("name", hardName.get(e.getKey()));
                    m.put("children", e.getValue());
                    return m;
                })
                .toList();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("children", rows);
        out.put("commonHard", common);
        out.put("commonMistakes", mistakeCount.entrySet().stream()
                .filter(e -> e.getValue() >= 2)
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5)
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("type", e.getKey());
                    m.put("label", Mistakes.label(e.getKey()));
                    m.put("tip", Mistakes.tip(e.getKey()));
                    m.put("children", e.getValue());
                    return m;
                })
                .toList());
        return out;
    }
}
