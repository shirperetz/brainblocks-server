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
        // 2026-11-13 (Shir: "a castle, not only a house - a house first, until it can be upgraded to a castle")
        add("house", "home", new String[] {"אוהל", "בית קטן", "בית גדול", "טירה"}, new int[] {10, 30, 60, 120},
                new String[] {"מקום לישון באי", "חלונות מוארים וארובה", "קומה שנייה ומרפסת", "חומות, מגדלים ודגלים"});
        // 2026-11-13 (Shir: "and if I bought everything?"): more levels for what had one or two
        add("farm", "food", new String[] {"ערוגת ירקות", "חווה", "חווה עם תרנגולות"}, new int[] {15, 40, 80},
                new String[] {"ירקות צבעוניים", "אסם וגדר", "לול ותרנגולות"});
        add("stall", "food", new String[] {"דוכן שוק", "שוק גדול"}, new int[] {20, 45},
                new String[] {"פירות ומרקיזה", "שלושה דוכנים צבעוניים"});
        add("mill", "production", new String[] {"טחנת רוח", "טחנה גדולה", "טחנה ומאפייה"}, new int[] {25, 50, 90},
                new String[] {"כנפיים שמסתובבות", "טחנה גבוהה יותר", "מאפייה עם ארובה"});
        add("lighthouse", "production", new String[] {"מגדלור", "מגדלור ובית שומר"}, new int[] {40, 85},
                new String[] {"אור שמאיר את הים", "בית קטן לשומר המגדלור"});
        add("tree", "decor", new String[] {"עץ", "עץ תפוחים"}, new int[] {5, 12}, new String[] {"עוד ירוק באי", "תפוחים אדומים"});
        add("palm", "decor", new String[] {"דקל"}, new int[] {6}, new String[] {"חוף טרופי"});
        add("flowers", "decor", new String[] {"ערוגת פרחים", "גן פרחים"}, new int[] {4, 10},
                new String[] {"צבע ליד הבית", "גן גדול עם ספסל"});
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

    /*
     * 2026-11-13 (Shir: "and if I bought everything?" -> a new island in another season): island 0 is
     * the first one; when the newest island is at its full size the next one can be bought. Each island
     * keeps its own places (stored slot = island * 1000 + place, land = island * 1000 + 100 + n, the
     * island itself = kind "isle", slot = its number). Prices grow a little on every new island.
     */
    public static final String[] SEASONS = {"summer", "snow", "desert", "sunset"};
    private static final String[] ISLE_NAMES = {"אי הקיץ", "אי השלג", "אי המדבר", "אי השקיעה"};

    public static String seasonOf(int isle) {
        return isle == 0 ? SEASONS[0] : SEASONS[1 + (isle - 1) % (SEASONS.length - 1)];
    }

    public static String isleName(int isle) {
        if (isle == 0) return ISLE_NAMES[0];
        int round = (isle - 1) / (SEASONS.length - 1);
        String base = ISLE_NAMES[1 + (isle - 1) % (SEASONS.length - 1)];
        return round == 0 ? base : base + " " + (round + 1);
    }

    /** What a price is on this island: +25% for every island after the first. */
    public static int price(int base, int isle) {
        return (int) Math.ceil(base * (1 + 0.25 * isle));
    }

    /** The n-th new island (1, 2, ...) costs this. */
    public static int isleCost(int n) {
        return 150 * n;
    }

    private static int isleOf(LearnIslandItem it) {
        return it.getKind().equals("isle") ? it.getSlot() : Math.max(0, it.getSlot()) / 1000;
    }

    private static int placeOf(LearnIslandItem it) {
        return Math.max(0, it.getSlot()) % 1000;
    }

    /** How many islands this child has (1 + the bought ones). */
    private static int isleCount(List<LearnIslandItem> mine) {
        int n = 1;
        for (LearnIslandItem it : mine) if (it.getKind().equals("isle")) n = Math.max(n, it.getSlot() + 1);
        return n;
    }

    private static int landsOn(List<LearnIslandItem> mine, int isle) {
        int n = 0;
        for (LearnIslandItem it : mine) if (it.getKind().equals("land") && isleOf(it) == isle) n++;
        return n;
    }

    private final LearnIslandItemRepository items;
    private final LearnerStageProgressRepository stages;
    private final LearnIsleProgressRepository isleProgress;
    private final IsleService isles;
    private final com.example.brainblocks.learning.repository.LearnerSessionRepository sessions;

    public IslandShopService(LearnIslandItemRepository items, LearnerStageProgressRepository stages,
                             LearnIsleProgressRepository isleProgress, IsleService isles,
                             com.example.brainblocks.learning.repository.LearnerSessionRepository sessions) {
        this.items = items;
        this.stages = stages;
        this.isleProgress = isleProgress;
        this.isles = isles;
        this.sessions = sessions;
    }

    /*
     * 2026-11-13 (Shir chose "a daily task and a streak"): the daily challenge pays coins - 5 for every
     * day it was done, and +10 more on every 3rd day in a row (3, 6, 9...). Counted from the saved
     * rounds, so nothing new is stored.
     */
    public static final int DAILY_COINS = 5;
    public static final int STREAK_BONUS = 10;

    public static int dailyCoins(java.util.Collection<java.time.LocalDate> days) {
        java.util.List<java.time.LocalDate> sorted = new java.util.ArrayList<>(new java.util.TreeSet<>(days));
        int coins = 0;
        int run = 0;
        java.time.LocalDate prev = null;
        for (java.time.LocalDate d : sorted) {
            run = prev != null && prev.plusDays(1).equals(d) ? run + 1 : 1;
            coins += DAILY_COINS + (run % 3 == 0 ? STREAK_BONUS : 0);
            prev = d;
        }
        return coins;
    }

    private java.util.Set<java.time.LocalDate> dailyDays(Long profileId) {
        java.util.Set<java.time.LocalDate> days = new java.util.HashSet<>();
        for (var s : sessions.findByProfileId(profileId)) {
            if (s.getMode() == com.example.brainblocks.learning.model.SessionMode.DAILY && s.getCreatedAt() != null) {
                days.add(s.getCreatedAt().toLocalDate());
            }
        }
        return days;
    }

    private int earnedStars(Long profileId) {
        int s = 0;
        for (LearnerStageProgress p : stages.findByProfileId(profileId)) s += p.getStarsEarned();
        for (LearnIsleProgress p : isleProgress.findByProfileId(profileId)) s += p.getStarsEarned();
        return s;
    }

    private static int spentOn(LearnIslandItem it) {
        if (it.getKind().equals("isle")) return isleCost(it.getSlot());
        int isle = isleOf(it);
        if (it.getKind().equals("land")) return price(landCost(placeOf(it) - 100), isle);
        Kind k = CATALOG.get(it.getKind());
        if (k == null) return 0;
        int s = 0;
        for (int i = 0; i < it.getLevel() && i < k.costs().length; i++) s += price(k.costs()[i], isle);
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
        return state(profileId, 0);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> state(Long profileId, int isle) {
        List<LearnIslandItem> mine = items.findByProfileId(profileId);
        int count = isleCount(mine);
        if (isle < 0 || isle >= count) throw bad("האי הזה עוד לא שלך");
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
            if (it.getKind().equals("isle") || isleOf(it) != isle) continue;
            if (it.getKind().equals("land")) {
                lands++;
                continue;
            }
            Map<String, Object> b = new LinkedHashMap<>();
            b.put("slot", placeOf(it));
            b.put("kind", it.getKind());
            b.put("level", it.getLevel());
            buildings.add(b);
        }
        int open = Math.min(MAX_SLOTS, OPEN_SLOTS + lands * 2);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("stars", stars);
        int daily = dailyCoins(dailyDays(profileId));
        out.put("dailyCoins", daily);
        out.put("coins", stars + chests * CHEST_COINS + bonus + daily - spent);
        out.put("openSlots", open);
        out.put("maxSlots", MAX_SLOTS);
        out.put("nextLandCost", open >= MAX_SLOTS ? null : price(landCost(lands), isle));
        out.put("isle", isle);
        out.put("season", seasonOf(isle));
        List<Map<String, Object>> isleList = new ArrayList<>();
        for (int i = 0; i < count; i++) isleList.add(Map.of("index", i, "season", seasonOf(i), "name", isleName(i)));
        out.put("isles", isleList);
        // 2026-11-13 (Shir: "until the whole island is built, another one doesn't open"): the newest
        // island must be at its full size AND have something built on every one of its places.
        int lastIsle = count - 1;
        long builtOnLast = mine.stream().filter(it -> CATALOG.containsKey(it.getKind()) && isleOf(it) == lastIsle).count();
        boolean lastFull = OPEN_SLOTS + landsOn(mine, lastIsle) * 2 >= MAX_SLOTS && builtOnLast >= MAX_SLOTS;
        out.put("lastIsleBuilt", builtOnLast);
        out.put("lastIsleName", isleName(lastIsle));
        out.put("nextIsle", lastFull
                ? Map.of("index", count, "season", seasonOf(count), "name", isleName(count), "cost", isleCost(count))
                : null);
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
            int[] costs = new int[k.costs().length];
            for (int i = 0; i < costs.length; i++) costs[i] = price(k.costs()[i], isle);
            c.put("costs", costs);
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

    public Map<String, Object> build(Long profileId, int slot, String kind) {
        return build(profileId, 0, slot, kind);
    }

    private LearnIslandItem buildingAt(Long profileId, int isle, int slot) {
        int stored = isle * 1000 + slot;
        return items.findByProfileId(profileId).stream()
                .filter(i -> i.getSlot() == stored && CATALOG.containsKey(i.getKind())).findFirst()
                .orElseThrow(() -> bad("אין כאן מבנה"));
    }

    @Transactional
    public Map<String, Object> build(Long profileId, int isle, int slot, String kind) {
        Kind k = CATALOG.get(kind);
        if (k == null) throw bad("אין דבר כזה בחנות");
        Map<String, Object> st = state(profileId, isle);
        if (slot < 0 || slot >= ((Number) st.get("openSlots")).intValue()) throw bad("המקום הזה עוד לא שלך");
        int stored = isle * 1000 + slot;
        boolean taken = items.findByProfileId(profileId).stream()
                .anyMatch(i -> i.getSlot() == stored && CATALOG.containsKey(i.getKind()));
        if (taken) throw bad("כבר יש כאן משהו");
        if (((Number) st.get("coins")).intValue() < price(k.costs()[0], isle)) throw poor();
        items.save(new LearnIslandItem(profileId, kind, stored, 1));
        return state(profileId, isle);
    }

    public Map<String, Object> upgrade(Long profileId, int slot) {
        return upgrade(profileId, 0, slot);
    }

    @Transactional
    public Map<String, Object> upgrade(Long profileId, int isle, int slot) {
        Map<String, Object> st = state(profileId, isle); // the island must be hers
        LearnIslandItem it = buildingAt(profileId, isle, slot);
        Kind k = CATALOG.get(it.getKind());
        if (it.getLevel() >= k.costs().length) throw bad("זו כבר הרמה הכי גבוהה");
        if (((Number) st.get("coins")).intValue() < price(k.costs()[it.getLevel()], isle)) throw poor();
        it.levelUp();
        items.save(it);
        return state(profileId, isle);
    }

    public Map<String, Object> remove(Long profileId, int slot) {
        return remove(profileId, 0, slot);
    }

    /** Takes a building down so something else can stand there; what it cost comes back in full. */
    @Transactional
    public Map<String, Object> remove(Long profileId, int isle, int slot) {
        state(profileId, isle);
        items.delete(buildingAt(profileId, isle, slot));
        return state(profileId, isle);
    }

    public Map<String, Object> buyLand(Long profileId) {
        return buyLand(profileId, 0);
    }

    @Transactional
    public Map<String, Object> buyLand(Long profileId, int isle) {
        Map<String, Object> st = state(profileId, isle);
        Object cost = st.get("nextLandCost");
        if (cost == null) throw bad("האי כבר בגודל המלא");
        if (((Number) st.get("coins")).intValue() < ((Number) cost).intValue()) throw poor();
        int n = (((Number) st.get("openSlots")).intValue() - OPEN_SLOTS) / 2;
        items.save(new LearnIslandItem(profileId, "land", isle * 1000 + 100 + n, 1));
        return state(profileId, isle);
    }

    /** Buys the next island - only when the newest one is at its full size. */
    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String, Object> buyIsle(Long profileId) {
        Map<String, Object> st = state(profileId, 0);
        Map<String, Object> next = (Map<String, Object>) st.get("nextIsle");
        if (next == null) throw bad("קודם בונים את כל האי האחרון - כל 32 המקומות");
        if (((Number) st.get("coins")).intValue() < ((Number) next.get("cost")).intValue()) throw poor();
        int n = ((Number) next.get("index")).intValue();
        items.save(new LearnIslandItem(profileId, "isle", n, 1));
        return state(profileId, n);
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
