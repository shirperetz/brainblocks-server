package com.example.brainblocks.learning.service;

import com.example.brainblocks.learning.model.LearnIslandItem;
import com.example.brainblocks.learning.model.LearnIsleProgress;
import com.example.brainblocks.learning.model.LearnerStageProgress;
import com.example.brainblocks.learning.repository.LearnIslandItemRepository;
import com.example.brainblocks.learning.repository.LearnIsleProgressRepository;
import com.example.brainblocks.learning.repository.LearnerStageProgressRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 2026-11-13 (Shir: "the stars buy things - build an island, upgrade it, get something back"; she
 * chose the live 3D island). A coin for every star the child earned, plus treasure chests; the server
 * keeps what is built and checks every price and place - the screen only asks.
 */
@Service
public class IslandShopService {

    /** One thing to build: its levels' names, prices and what each level gives. */
    public record Kind(String key, String category, String[] names, int[] costs, String[] perks) {}

    public static final Map<String, Kind> CATALOG = new LinkedHashMap<>();

    private static void add(String key, String cat, String[] names, int[] costs, String[] perks) {
        CATALOG.put(key, new Kind(key, cat, names, costs, perks));
    }

    static {
        add("house", "home", new String[] {"אוהל", "בית קטן", "בית גדול"}, new int[] {10, 30, 60},
                new String[] {"מקום לישון באי", "חלונות מוארים וארובה", "קומה שנייה ומרפסת"});
        add("farm", "food", new String[] {"ערוגת ירקות", "חווה"}, new int[] {15, 40},
                new String[] {"ירקות צבעוניים", "אסם וגדר"});
        add("stall", "food", new String[] {"דוכן שוק"}, new int[] {20}, new String[] {"פירות ומרקיזה"});
        add("mill", "production", new String[] {"טחנת רוח", "טחנה גדולה"}, new int[] {25, 50},
                new String[] {"כנפיים שמסתובבות", "טחנה גבוהה יותר"});
        add("lighthouse", "production", new String[] {"מגדלור"}, new int[] {40}, new String[] {"אור שמאיר את הים"});
        add("tree", "decor", new String[] {"עץ"}, new int[] {5}, new String[] {"עוד ירוק באי"});
        add("palm", "decor", new String[] {"דקל"}, new int[] {6}, new String[] {"חוף טרופי"});
        add("flowers", "decor", new String[] {"ערוגת פרחים"}, new int[] {4}, new String[] {"צבע ליד הבית"});
        add("boat", "transport", new String[] {"סירת מפרש"}, new int[] {30}, new String[] {"סירה ליד החוף"});
        add("ship", "transport", new String[] {"ספינה", "ספינת מפרשים גדולה"}, new int[] {80, 150},
                new String[] {"ספינה עם תורן ומפרשים", "שני תרנים ודגל"});
    }

    public static final int OPEN_SLOTS = 8;
    public static final int MAX_SLOTS = 32; // 2026-11-13 (Shir filled all 16: "there is nothing more to build")
    public static final int CHEST_COINS = 20;

    /** The n-th piece of land (0, 1, ...) costs this; each opens 2 more places. */
    public static int landCost(int n) {
        return 25 * (n + 1);
    }

    private final LearnIslandItemRepository items;
    private final LearnerStageProgressRepository stages;
    private final LearnIsleProgressRepository isleProgress;
    private final IsleService isles;

    public IslandShopService(LearnIslandItemRepository items, LearnerStageProgressRepository stages,
                             LearnIsleProgressRepository isleProgress, IsleService isles) {
        this.items = items;
        this.stages = stages;
        this.isleProgress = isleProgress;
        this.isles = isles;
    }

    private int earnedStars(Long profileId) {
        int s = 0;
        for (LearnerStageProgress p : stages.findByProfileId(profileId)) s += p.getStarsEarned();
        for (LearnIsleProgress p : isleProgress.findByProfileId(profileId)) s += p.getStarsEarned();
        return s;
    }

    private static int spentOn(LearnIslandItem it) {
        if (it.getKind().equals("land")) return landCost(it.getSlot() - 100);
        Kind k = CATALOG.get(it.getKind());
        if (k == null) return 0;
        int s = 0;
        for (int i = 0; i < it.getLevel() && i < k.costs().length; i++) s += k.costs()[i];
        return s;
    }

    /** The islands of the sea this child has finished (the next one opened): each has a treasure chest. */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> finishedIsles(Long profileId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> m : (List<Map<String, Object>>) isles.sea(profileId).get("isles")) {
            if (Boolean.TRUE.equals(m.get("open"))
                    && ((Number) m.get("passed")).intValue() >= ((Number) m.get("needed")).intValue()) out.add(m);
        }
        return out;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> state(Long profileId) {
        List<LearnIslandItem> mine = items.findByProfileId(profileId);
        int stars = earnedStars(profileId);
        int chests = 0;
        int spent = 0;
        int lands = 0;
        int bonus = 0;
        List<Map<String, Object>> buildings = new ArrayList<>();
        Set<String> opened = new HashSet<>();
        for (LearnIslandItem it : mine) {
            // 2026-11-13 (Shir: "a million coins, only for me, to test"): coins given by hand in the DB
            // (kind "bonus", level = how many). No route creates one.
            if (it.getKind().equals("bonus")) {
                bonus += it.getLevel();
                continue;
            }
            if (it.getKind().startsWith("chest:")) {
                chests++;
                opened.add(it.getKind().substring(6));
                continue;
            }
            spent += spentOn(it);
            if (it.getKind().equals("land")) {
                lands++;
                continue;
            }
            Map<String, Object> b = new LinkedHashMap<>();
            b.put("slot", it.getSlot());
            b.put("kind", it.getKind());
            b.put("level", it.getLevel());
            buildings.add(b);
        }
        int open = Math.min(MAX_SLOTS, OPEN_SLOTS + lands * 2);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("stars", stars);
        out.put("coins", stars + chests * CHEST_COINS + bonus - spent);
        out.put("openSlots", open);
        out.put("maxSlots", MAX_SLOTS);
        out.put("nextLandCost", open >= MAX_SLOTS ? null : landCost(lands));
        out.put("buildings", buildings);
        List<Map<String, Object>> chestList = new ArrayList<>();
        for (Map<String, Object> m : finishedIsles(profileId)) {
            if (opened.contains((String) m.get("key"))) continue;
            chestList.add(Map.of("isleKey", m.get("key"), "name", m.get("name"), "coins", CHEST_COINS));
        }
        out.put("chests", chestList);
        List<Map<String, Object>> cat = new ArrayList<>();
        for (Kind k : CATALOG.values()) {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("kind", k.key());
            c.put("category", k.category());
            c.put("names", k.names());
            c.put("costs", k.costs());
            c.put("perks", k.perks());
            cat.add(c);
        }
        out.put("catalog", cat);
        return out;
    }

    private static ResponseStatusException bad(String why) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, why);
    }

    private static ResponseStatusException poor() {
        return new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED, "אין מספיק מטבעות");
    }

    @Transactional
    public Map<String, Object> build(Long profileId, int slot, String kind) {
        Kind k = CATALOG.get(kind);
        if (k == null) throw bad("אין דבר כזה בחנות");
        Map<String, Object> st = state(profileId);
        if (slot < 0 || slot >= ((Number) st.get("openSlots")).intValue()) throw bad("המקום הזה עוד לא שלך");
        boolean taken = items.findByProfileId(profileId).stream()
                .anyMatch(i -> i.getSlot() == slot && CATALOG.containsKey(i.getKind()));
        if (taken) throw bad("כבר יש כאן משהו");
        if (((Number) st.get("coins")).intValue() < k.costs()[0]) throw poor();
        items.save(new LearnIslandItem(profileId, kind, slot, 1));
        return state(profileId);
    }

    @Transactional
    public Map<String, Object> upgrade(Long profileId, int slot) {
        LearnIslandItem it = items.findByProfileId(profileId).stream()
                .filter(i -> i.getSlot() == slot && CATALOG.containsKey(i.getKind())).findFirst()
                .orElseThrow(() -> bad("אין כאן מבנה"));
        Kind k = CATALOG.get(it.getKind());
        if (it.getLevel() >= k.costs().length) throw bad("זו כבר הרמה הכי גבוהה");
        if (((Number) state(profileId).get("coins")).intValue() < k.costs()[it.getLevel()]) throw poor();
        it.levelUp();
        items.save(it);
        return state(profileId);
    }

    /** Takes a building down so something else can stand there; what it cost comes back in full. */
    @Transactional
    public Map<String, Object> remove(Long profileId, int slot) {
        LearnIslandItem it = items.findByProfileId(profileId).stream()
                .filter(i -> i.getSlot() == slot && CATALOG.containsKey(i.getKind())).findFirst()
                .orElseThrow(() -> bad("אין כאן מבנה"));
        items.delete(it);
        return state(profileId);
    }

    @Transactional
    public Map<String, Object> buyLand(Long profileId) {
        Map<String, Object> st = state(profileId);
        Object cost = st.get("nextLandCost");
        if (cost == null) throw bad("האי כבר בגודל המלא");
        if (((Number) st.get("coins")).intValue() < ((Number) cost).intValue()) throw poor();
        int n = (((Number) st.get("openSlots")).intValue() - OPEN_SLOTS) / 2;
        items.save(new LearnIslandItem(profileId, "land", 100 + n, 1));
        return state(profileId);
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String, Object> openChest(Long profileId, String isleKey) {
        List<Map<String, Object>> ready = (List<Map<String, Object>>) state(profileId).get("chests");
        if (ready.stream().noneMatch(c -> c.get("isleKey").equals(isleKey))) throw bad("אין כאן תיבה לפתוח");
        items.save(new LearnIslandItem(profileId, "chest:" + isleKey, -1, 1));
        Map<String, Object> out = state(profileId);
        out.put("opened", CHEST_COINS);
        return out;
    }
}
