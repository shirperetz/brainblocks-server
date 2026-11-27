package com.example.brainblocks.learning.controller;

import com.example.brainblocks.config.LearnerAuthConfig;
import com.example.brainblocks.learning.model.LearnBadge;
import com.example.brainblocks.learning.model.LearnIslandItem;
import com.example.brainblocks.learning.model.LearnerProfile;
import com.example.brainblocks.learning.model.StageStatus;
import com.example.brainblocks.learning.repository.*;
import com.example.brainblocks.learning.service.BadgeService;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * 2026-11-27 (Shir: idea #6 - badges + a collection album). The child's earned badges (worked out live
 * from their own data) and the album of everything they built on their islands. A new badge pops up once.
 */
@RestController
public class BadgeController {

    private static final LocalDateTime EPOCH = LocalDateTime.of(2000, 1, 1, 0, 0);

    private final LearnerProfileRepository profiles;
    private final LearnerStageProgressRepository stages;
    private final LearnerAnswerRepository answers;
    private final LearnIslandItemRepository islandItems;
    private final LearnAwardRepository awards;
    private final LearnBadgeRepository badges;

    public BadgeController(LearnerProfileRepository profiles, LearnerStageProgressRepository stages,
                           LearnerAnswerRepository answers, LearnIslandItemRepository islandItems,
                           LearnAwardRepository awards, LearnBadgeRepository badges) {
        this.profiles = profiles;
        this.stages = stages;
        this.answers = answers;
        this.islandItems = islandItems;
        this.awards = awards;
        this.badges = badges;
    }

    private BadgeService.Counts counts(Long id) {
        LearnerProfile p = profiles.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var sp = stages.findByProfileId(id);
        int stars = sp.stream().mapToInt(s -> s.getStarsEarned()).sum();
        int done = (int) sp.stream().filter(s -> s.getStatus() == StageStatus.COMPLETED).count();
        long correct = answers.countByProfileIdAndCorrectTrueAndCreatedAtGreaterThanEqual(id, EPOCH);
        int buildings = (int) islandItems.findByProfileId(id).stream().filter(BadgeController::isBuilding).count();
        long awardCount = awards.countByProfileId(id);
        return new BadgeService.Counts(stars, correct, p.getDailyStreak(), done, buildings, awardCount);
    }

    static boolean isBuilding(LearnIslandItem it) {
        String k = it.getKind();
        return !k.equals("isle") && !k.equals("land") && !k.startsWith("bonus")
                && !k.startsWith("room:") && !k.startsWith("award:") && !k.startsWith("donate:") && !k.startsWith("chest:");
    }

    @GetMapping("/api/learn/profiles/{id}/badges")
    public Map<String, Object> list(@PathVariable Long id, @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        own(id, me);
        Set<String> seen = badges.findByProfileId(id).stream().map(LearnBadge::getBadgeKey).collect(Collectors.toSet());
        var all = BadgeService.badges(counts(id), seen);
        List<Map<String, Object>> album = islandItems.findByProfileId(id).stream().filter(BadgeController::isBuilding)
                .map(it -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("kind", it.getKind());
                    m.put("level", it.getLevel());
                    m.put("isle", it.getSlot() / 1000);
                    return m;
                }).toList();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("badges", all);
        out.put("earned", all.stream().filter(b -> (boolean) b.get("earned")).count());
        out.put("total", all.size());
        out.put("album", album);
        return out;
    }

    /** New badges the child hasn't seen - the home screen pops them up, then marks them seen. */
    @GetMapping("/api/learn/profiles/{id}/badges/new")
    public List<Map<String, Object>> fresh(@PathVariable Long id, @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        own(id, me);
        Set<String> seen = badges.findByProfileId(id).stream().map(LearnBadge::getBadgeKey).collect(Collectors.toSet());
        return BadgeService.badges(counts(id), seen).stream().filter(b -> (boolean) b.get("isNew")).toList();
    }

    @PostMapping("/api/learn/profiles/{id}/badges/seen")
    public Map<String, Object> seen(@PathVariable Long id, @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        own(id, me);
        Set<String> already = badges.findByProfileId(id).stream().map(LearnBadge::getBadgeKey).collect(Collectors.toSet());
        LocalDateTime now = LocalDateTime.now();
        int added = 0;
        for (String key : BadgeService.earnedKeys(counts(id))) {
            if (already.add(key)) {
                badges.save(new LearnBadge(id, key, now));
                added++;
            }
        }
        return Map.of("marked", added);
    }

    private static void own(Long id, Long me) {
        if (!id.equals(me)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found");
    }
}
