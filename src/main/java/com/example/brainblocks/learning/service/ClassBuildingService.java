package com.example.brainblocks.learning.service;

import com.example.brainblocks.learning.model.LearnClass;
import com.example.brainblocks.learning.model.LearnerProfile;
import com.example.brainblocks.learning.repository.LearnClassRepository;
import com.example.brainblocks.learning.repository.LearnerAnswerRepository;
import com.example.brainblocks.learning.repository.LearnerProfileRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 2026-11-12 (idea #6, "a shared class challenge"): the class builds one building together each week
 * (Sunday to Saturday) from everyone's right answers - 30 per child. Teamwork, not a race: a child
 * adds at most twice its share (one child can't build it alone), and nobody is ranked - only "how
 * many helped" and the child's own part. Counted from learn_answer, nothing stored.
 */
@Service
public class ClassBuildingService {

    public static final int PER_CHILD = 30;
    /** Floors of the picture: 0 empty lot, 1 foundations, 2-3 floors, 4 roof, 5 flag (done). */
    public static final int STAGES = 5;
    /**
     * 2026-11-14 (A5, Shir: "donate coins to the class building"): 2 coins = one brick (like one right
     * answer), at most 10 bricks a week from each child - coins help, practice still builds it.
     */
    public static final int COINS_PER_BRICK = 2;
    public static final int MAX_DONATED_BRICKS = 10;

    private final LearnClassRepository classes;
    private final LearnerProfileRepository profiles;
    private final LearnerAnswerRepository answers;
    private final AccessService access;
    private com.example.brainblocks.learning.repository.LearnIslandItemRepository items;
    private IslandShopService shop;

    public ClassBuildingService(LearnClassRepository classes, LearnerProfileRepository profiles,
                                LearnerAnswerRepository answers, AccessService access) {
        this.classes = classes;
        this.profiles = profiles;
        this.answers = answers;
        this.access = access;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setDonations(com.example.brainblocks.learning.repository.LearnIslandItemRepository items, IslandShopService shop) {
        this.items = items;
        this.shop = shop;
    }

    static String donationKind(LocalDateTime weekStart) {
        return "donate:" + weekStart.toLocalDate();
    }

    /** Coins this child gave the class building this week. */
    private int donatedCoins(Long childId, LocalDateTime since) {
        if (items == null) return 0;
        String kind = donationKind(since);
        return items.findByProfileId(childId).stream().filter(i -> kind.equals(i.getKind())).mapToInt(i -> i.getLevel()).sum();
    }

    /** The child gives [coins] from its island to its class's building this week. Returns the building. */
    @org.springframework.transaction.annotation.Transactional
    public Map<String, Object> donate(LearnerProfile child, int coins) {
        if (items == null || shop == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
        Map<String, Object> before = forChild(child);
        if (before == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "צריך להיות בכיתה כדי לתרום לבניין");
        if (Boolean.TRUE.equals(before.get("done"))) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "הבניין כבר גמור השבוע 🎉");
        if (coins < COINS_PER_BRICK || coins % COINS_PER_BRICK != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "תורמים בזוגות: כל 2 מטבעות = לבנה אחת");
        }
        int left = ((Number) before.get("canDonate")).intValue();
        if (coins > left) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, left == 0 ? "השבוע כבר תרמת את כל מה שאפשר - נתראה ביום ראשון!" : "אפשר לתרום השבוע עוד " + left + " מטבעות");
        }
        int wallet = ((Number) shop.state(child.getId()).get("coins")).intValue();
        if (wallet < coins) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "אין מספיק מטבעות");
        // One row a week per child (the table is unique on child + kind + place): add to it.
        String kind = donationKind(weekStart(LocalDate.now()));
        var row = items.findByProfileId(child.getId()).stream().filter(i -> kind.equals(i.getKind())).findFirst()
                .orElseGet(() -> new com.example.brainblocks.learning.model.LearnIslandItem(child.getId(), kind, -2, 0));
        row.setLevel(row.getLevel() + coins);
        items.save(row);
        Map<String, Object> after = forChild(child);
        after.put("coins", wallet - coins);
        return after;
    }

    /** Sunday 00:00 of this week (the Israeli school week). */
    static LocalDateTime weekStart(LocalDate today) {
        return today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY)).atStartOfDay();
    }

    /** How a week's numbers become the building. Pure, for the tests. */
    static Map<String, Object> picture(int children, List<Long> rightPerChild) {
        int target = PER_CHILD * Math.max(1, children);
        long cap = 2L * PER_CHILD;
        long built = 0;
        int helped = 0;
        for (long n : rightPerChild) {
            built += Math.min(n, cap);
            if (n > 0) helped++;
        }
        built = Math.min(built, target);
        int stage = (int) (built * STAGES / target);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("target", target);
        m.put("built", built);
        m.put("stage", stage);
        m.put("done", built >= target);
        m.put("children", children);
        m.put("helped", helped);
        return m;
    }

    public Map<String, Object> forClass(Long me, Long classId) {
        LearnerProfile v = access.requireStaff(me);
        LearnClass c = classes.findById(classId == null ? -1L : classId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "אין כיתה כזו"));
        if (access.role(v) != AccessService.Role.ADMIN && !c.getTeacherId().equals(v.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "זו לא כיתה שלך");
        }
        return build(c, null);
    }

    /** The child's class building (null when it has no class). */
    public Map<String, Object> forChild(LearnerProfile child) {
        if (child.getClassId() == null) return null;
        return classes.findById(child.getClassId()).map(c -> build(c, child.getId())).orElse(null);
    }

    private Map<String, Object> build(LearnClass c, Long childId) {
        LocalDateTime since = weekStart(LocalDate.now());
        List<LearnerProfile> kids = profiles.findAll().stream()
                .filter(p -> c.getId().equals(p.getClassId()) && !p.isTeacher() && !p.isAdmin() && p.isApproved() && !p.isDisabled())
                .toList();
        Map<Long, Long> right = new LinkedHashMap<>();
        int donatedAll = 0;
        int myDonated = 0;
        for (LearnerProfile k : kids) {
            long answered = answers.countByProfileIdAndCorrectTrueAndCreatedAtGreaterThanEqual(k.getId(), since);
            int given = donatedCoins(k.getId(), since);
            donatedAll += given;
            if (k.getId().equals(childId)) myDonated = given;
            // 2026-11-14 (A5): donated coins are bricks too (still within the "twice your share" cap).
            right.put(k.getId(), answered + given / COINS_PER_BRICK);
        }
        Map<String, Object> m = picture(kids.size(), List.copyOf(right.values()));
        m.put("className", c.getName());
        m.put("weekStart", since.toLocalDate().toString());
        m.put("donatedCoins", donatedAll);
        if (childId != null) {
            m.put("mine", right.getOrDefault(childId, 0L));
            m.put("myDonated", myDonated);
            m.put("canDonate", Math.max(0, MAX_DONATED_BRICKS * COINS_PER_BRICK - myDonated));
            m.put("coinsPerBrick", COINS_PER_BRICK);
        }
        return m;
    }
}
