package com.example.brainblocks.learning.controller;

import com.example.brainblocks.config.LearnerAuthConfig;
import com.example.brainblocks.learning.model.LearnAward;
import com.example.brainblocks.learning.model.LearnIslandItem;
import com.example.brainblocks.learning.model.LearnerProfile;
import com.example.brainblocks.learning.repository.LearnAwardRepository;
import com.example.brainblocks.learning.repository.LearnIslandItemRepository;
import com.example.brainblocks.learning.repository.LearnerProfileRepository;
import com.example.brainblocks.learning.service.AccessService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * 2026-11-27 (Shir: idea #10 - a prize from the teacher): a badge, coins for the island and a personal
 * sentence, to one child or to the whole class. The child sees it once (a card on the home screen).
 */
@RestController
public class AwardController {

    static final Set<String> EMOJI = Set.of("⭐", "🏆", "💪", "🎉", "🧠", "❤️");
    static final Set<Integer> COINS = Set.of(0, 5, 10, 20);
    static final int PER_CHILD_A_DAY = 3;
    static final int PER_TEACHER_A_DAY = 300;

    private final AccessService access;
    private final LearnAwardRepository awards;
    private final LearnIslandItemRepository islandItems;
    private final LearnerProfileRepository profiles;

    public AwardController(AccessService access, LearnAwardRepository awards, LearnIslandItemRepository islandItems,
                           LearnerProfileRepository profiles) {
        this.access = access;
        this.awards = awards;
        this.islandItems = islandItems;
        this.profiles = profiles;
    }

    public static class Body {
        public Long childId;
        public Long classId;
        public String emoji;
        public Integer coins;
        public String message;
    }

    @PostMapping("/api/learn/staff/awards")
    @Transactional
    public Map<String, Object> give(@RequestBody Body b, @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        LearnerProfile teacher = access.requireStaff(me);
        String emoji = b.emoji == null ? "⭐" : b.emoji;
        int coins = b.coins == null ? 0 : b.coins;
        if (!EMOJI.contains(emoji) || !COINS.contains(coins)) throw bad("פרס לא תקין");
        String message = clean(b.message);
        if (message.isEmpty() && coins == 0) throw bad("כתבו משפט אישי או בחרו מטבעות");

        List<LearnerProfile> to;
        if (b.childId != null) {
            access.requireCanSee(me, b.childId);
            to = List.of(profiles.findById(b.childId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
        } else if (b.classId != null && b.classId > 0) {
            to = access.visibleChildren(me, b.classId);
        } else {
            throw bad("למי הפרס?");
        }
        to = to.stream().filter(p -> p.isApproved() && !p.isDisabled() && !p.isTeacher() && !p.isAdmin()).toList();
        if (to.isEmpty()) throw bad("אין בכיתה ילדים מאושרים");

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime dayAgo = now.minusDays(1);
        if (awards.countByFromIdAndCreatedAtAfter(me, dayAgo) + to.size() > PER_TEACHER_A_DAY) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "היום כבר נתתם הרבה פרסים - אפשר שוב מחר");
        }
        List<String> skipped = new ArrayList<>();
        int given = 0;
        for (LearnerProfile kid : to) {
            if (awards.countByProfileIdAndCreatedAtAfter(kid.getId(), dayAgo) >= PER_CHILD_A_DAY) {
                skipped.add(kid.getName());
                continue;
            }
            LearnAward a = awards.save(new LearnAward(kid.getId(), me, teacher.getName(), b.childId == null ? b.classId : null,
                    emoji, coins, message, now));
            if (coins > 0) islandItems.save(new LearnIslandItem(kid.getId(), "award:" + a.getId(), 0, coins));
            given++;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("given", given);
        out.put("skipped", skipped);
        return out;
    }

    /** The child's prizes, for the teacher's report card (newest first). */
    @GetMapping("/api/learn/staff/children/{id}/awards")
    public List<Map<String, Object>> ofChild(@PathVariable Long id, @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        access.requireStaff(me);
        access.requireCanSee(me, id);
        return awards.findTop20ByProfileIdOrderByCreatedAtDesc(id).stream().map(AwardController::view).toList();
    }

    /** Prizes the child hasn't seen yet (the home screen shows them, then marks them seen). */
    @GetMapping("/api/learn/profiles/{id}/awards")
    public List<Map<String, Object>> unseen(@PathVariable Long id, @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        own(id, me);
        return awards.findByProfileIdAndSeenAtIsNullOrderByCreatedAtAsc(id).stream().map(AwardController::view).toList();
    }

    @PostMapping("/api/learn/profiles/{id}/awards/seen")
    public Map<String, Object> seen(@PathVariable Long id, @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        own(id, me);
        var list = awards.findByProfileIdAndSeenAtIsNullOrderByCreatedAtAsc(id);
        LocalDateTime now = LocalDateTime.now();
        list.forEach(a -> a.setSeenAt(now));
        awards.saveAll(list);
        return Map.of("seen", list.size());
    }

    private static Map<String, Object> view(LearnAward a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("emoji", a.getEmoji());
        m.put("coins", a.getCoins());
        m.put("message", a.getMessage());
        m.put("from", a.getFromName());
        m.put("wholeClass", a.getClassId() != null);
        m.put("createdAt", a.getCreatedAt().toString());
        m.put("seen", a.getSeenAt() != null);
        return m;
    }

    private static void own(Long id, Long me) {
        if (!id.equals(me)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found");
    }

    /** One line, no hidden/direction characters, at most 140. */
    static String clean(String s) {
        if (s == null) return "";
        String t = s.replaceAll("[\\p{Cc}\\p{Cf}]", " ").replaceAll("\\s+", " ").trim();
        return t.length() > 140 ? t.substring(0, 140) : t;
    }

    private static ResponseStatusException bad(String m) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, m);
    }
}
