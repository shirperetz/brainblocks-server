package com.example.brainblocks.learning.service;

import com.example.brainblocks.learning.model.LearnerProfile;
import com.example.brainblocks.learning.model.LearnerStageProgress;
import com.example.brainblocks.learning.model.StageStatus;
import com.example.brainblocks.learning.model.Topic;
import com.example.brainblocks.learning.repository.LearnerProfileRepository;
import com.example.brainblocks.learning.repository.LearnerStageProgressRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * 2026-11-11 (Shir: "a map, and at every level they pass they get something, and when they finish
 * they can build the island - so they have a goal to finish everything"). Every passed level opens one
 * spot ("ADDITION-3"); the child picks what stands there (the client offers 3 items per spot). A spot
 * can only be filled once its level is COMPLETED - checked here, from the real stage progress.
 */
@Service
public class IslandService {

    /** "ADDITION-3" */
    static final Pattern SPOT = Pattern.compile("([A-Z_]{3,20})-([1-6])");
    /** One emoji (with skin/joiner/variation marks), never text: max 16 chars, no letters or digits. */
    static final Pattern ITEM = Pattern.compile("[^\\p{L}\\p{N}\\s<>\"'&]{1,16}");

    private final LearnerProfileRepository profiles;
    private final LearnerStageProgressRepository stages;
    private final ObjectMapper json = new ObjectMapper();

    public IslandService(LearnerProfileRepository profiles, LearnerStageProgressRepository stages) {
        this.profiles = profiles;
        this.stages = stages;
    }

    public Map<String, Object> island(Long profileId) {
        LearnerProfile p = profiles.findById(profileId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found"));
        List<Map<String, Object>> earned = new ArrayList<>();
        for (LearnerStageProgress s : stages.findByProfileId(profileId)) {
            if (s.getStatus() != StageStatus.COMPLETED) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("spot", s.getTopic().name() + "-" + s.getStageNumber());
            m.put("stars", s.getStarsEarned());
            earned.add(m);
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("grade", p.getGrade());
        r.put("earned", earned);
        r.put("choices", choices(p));
        return r;
    }

    public Map<String, Object> choose(Long profileId, String spot, String item) {
        var m = spot == null ? null : SPOT.matcher(spot);
        if (m == null || !m.matches()) throw bad("מקום לא מוכר");
        Topic topic;
        try {
            topic = Topic.valueOf(m.group(1));
        } catch (IllegalArgumentException e) {
            throw bad("מקום לא מוכר");
        }
        int stage = Integer.parseInt(m.group(2));
        if (item == null || !ITEM.matcher(item).matches()) throw bad("פריט לא מוכר");
        boolean done = stages.findByProfileIdAndTopicAndStageNumber(profileId, topic, stage)
                .map(s -> s.getStatus() == StageStatus.COMPLETED).orElse(false);
        if (!done) throw new ResponseStatusException(HttpStatus.CONFLICT, "עוד לא עברת את השלב הזה");
        LearnerProfile p = profiles.findById(profileId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found"));
        Map<String, String> c = choices(p);
        c.put(spot, item);
        try {
            p.setIslandJson(json.writeValueAsString(c));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        profiles.save(p);
        return island(profileId);
    }

    private Map<String, String> choices(LearnerProfile p) {
        if (p.getIslandJson() == null || p.getIslandJson().isBlank()) return new TreeMap<>();
        try {
            return new TreeMap<>(json.readValue(p.getIslandJson(), new TypeReference<Map<String, String>>() {}));
        } catch (Exception e) {
            return new TreeMap<>(); // a broken value never breaks the game
        }
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
