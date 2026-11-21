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
    /** season: null = on every island; "snow" etc. = only on that season's islands (2026-11-13). */
    public record Kind(String key, String category, String[] names, int[] costs, String[] perks, String season) {}

    public static final Map<String, Kind> CATALOG = new LinkedHashMap<>();

    private static void add(String key, String cat, String[] names, int[] costs, String[] perks) {
        CATALOG.put(key, new Kind(key, cat, names, costs, perks, null));
    }

    private static void addSeason(String season, String key, String cat, String[] names, int[] costs, String[] perks) {
        CATALOG.put(key, new Kind(key, cat, names, costs, perks, season));
    }

    static {
        // 2026-11-13 (Shir: "a castle, not only a house - a house first, until it can be upgraded to a castle")
        add("house", "home", new String[] {"אוהל", "בית קטן", "בית גדול", "טירה", "ארמון זהב"}, new int[] {10, 30, 60, 120, 250},
                new String[] {"מקום לישון באי", "חלונות מוארים וארובה", "קומה שנייה ומרפסת", "חומות, מגדלים ודגלים",
                        "גגות זהב, כיפה ודגלים מתנופפים"});
        // 2026-11-13 (Shir: "and if I bought everything?"): more levels for what had one or two
        add("farm", "food", new String[] {"ערוגת ירקות", "חווה", "חווה עם תרנגולות"}, new int[] {15, 40, 80},
                new String[] {"ירקות צבעוניים", "אסם וגדר", "לול ותרנגולות"});
        add("stall", "food", new String[] {"דוכן שוק", "שוק גדול"}, new int[] {20, 45},
                new String[] {"פירות ומרקיזה", "שלושה דוכנים צבעוניים"});
        add("mill", "production", new String[] {"טחנת רוח", "טחנה גדולה", "טחנה ומאפייה"}, new int[] {25, 50, 90},
                new String[] {"כנפיים שמסתובבות", "טחנה גבוהה יותר", "מאפייה עם ארובה"});
        add("lighthouse", "production", new String[] {"מגדלור", "מגדלור ובית שומר"}, new int[] {40, 85},
                new String[] {"אור שמאיר את הים", "בית קטן לשומר המגדלור"});
        add("tree", "decor", new String[] {"עץ", "עץ תפוחים", "עץ ענק"}, new int[] {5, 12, 30},
                new String[] {"עוד ירוק באי", "תפוחים אדומים", "עץ עתיק ורחב עם נדנדה"});
        add("palm", "decor", new String[] {"דקל", "דקל עם קוקוסים"}, new int[] {6, 14}, new String[] {"חוף טרופי", "קוקוסים וערסל"});
        add("flowers", "decor", new String[] {"ערוגת פרחים", "גן פרחים"}, new int[] {4, 10},
                new String[] {"צבע ליד הבית", "גן גדול עם ספסל"});
        add("boat", "transport", new String[] {"סירת מפרש", "סירת דייגים"}, new int[] {30, 55},
                new String[] {"סירה ליד החוף", "רשת דייגים ודגים"});
        add("ship", "transport", new String[] {"ספינה", "ספינת מפרשים גדולה", "ספינת פיראטים"}, new int[] {80, 150, 260},
                new String[] {"ספינה עם תורן ומפרשים", "שני תרנים ודגל", "מפרשים שחורים, תותחים ותיבת אוצר"});
        // 2026-11-13 (Shir: "upgrade what's inside the islands - change, add, everything")
        add("school", "home", new String[] {"בית ספר", "בית ספר עם פעמון"}, new int[] {50, 100},
                new String[] {"כיתה קטנה באי", "מגדל פעמון ומגרש"});
        add("well", "production", new String[] {"באר"}, new int[] {8}, new String[] {"מים לכל האי"});
        add("fountain", "decor", new String[] {"מזרקה", "מזרקה גדולה"}, new int[] {20, 45},
                new String[] {"מים שקופצים למעלה", "שתי קומות של מים"});
        add("playground", "decor", new String[] {"גן שעשועים"}, new int[] {35}, new String[] {"מגלשה ונדנדה"});
        add("pond", "decor", new String[] {"בריכת דגים", "בריכה עם ברווזים"}, new int[] {15, 30},
                new String[] {"דגים כתומים", "ברווזים שוחים"});
        add("sheep", "animals", new String[] {"כבשים", "עדר כבשים"}, new int[] {12, 28},
                new String[] {"שתי כבשים לבנות", "עדר וגדר"});
        add("cow", "animals", new String[] {"פרה", "רפת"}, new int[] {18, 40}, new String[] {"פרה מנוקדת", "רפת עם שתי פרות"});
        add("horse", "animals", new String[] {"סוס", "אורווה"}, new int[] {25, 55}, new String[] {"סוס חום", "אורווה וגדר"});
        // only on one season's island
        addSeason("snow", "snowman", "decor", new String[] {"איש שלג", "משפחת אנשי שלג"}, new int[] {6, 15},
                new String[] {"עם אף גזר וכובע", "שלושה אנשי שלג"});
        addSeason("snow", "igloo", "home", new String[] {"איגלו"}, new int[] {30}, new String[] {"בית קרח עגול"});
        addSeason("snow", "penguin", "animals", new String[] {"פינגווינים"}, new int[] {20}, new String[] {"שלושה פינגווינים"});
        addSeason("desert", "cactus", "decor", new String[] {"קקטוס", "קקטוס פורח"}, new int[] {5, 12},
                new String[] {"ירוק וקוצני", "פרחים ורודים"});
        addSeason("desert", "camel", "animals", new String[] {"גמל"}, new int[] {25}, new String[] {"גמל עם שתי דבשות"});
        addSeason("desert", "pyramid", "production", new String[] {"פירמידה"}, new int[] {90}, new String[] {"פירמידת זהב"});
        addSeason("sunset", "lantern", "decor", new String[] {"פנסים", "שדרת פנסים"}, new int[] {8, 20},
                new String[] {"אור חם בערב", "שישה פנסים דולקים"});
        addSeason("sunset", "campfire", "decor", new String[] {"מדורה"}, new int[] {12}, new String[] {"אש וספסלים"});
        // further islands' prizes
        add("balloon", "transport", new String[] {"כדור פורח", "כדור פורח גדול"}, new int[] {70, 140},
                new String[] {"מרחף מעל האי", "סל גדול ופסים צבעוניים"});
        add("rocket", "transport", new String[] {"חללית"}, new int[] {220}, new String[] {"חללית שמוכנה לטוס לירח"});
        // 2026-11-21 (Shir: "more crazy things for the islands - invest"): the wonders (3D in island3d/wonders.js)
        add("icecream", "food", new String[] {"גלידריה"}, new int[] {30}, new String[] {"גלידה בשלוש קומות"});
        add("rainbow", "magic", new String[] {"קשת בענן"}, new int[] {25}, new String[] {"קשת בכל הצבעים עם עננים"});
        add("chest", "magic", new String[] {"תיבת אוצר"}, new int[] {40}, new String[] {"נפתחת לבד - מטבעות ויהלום"});
        add("carousel", "fun", new String[] {"קרוסלה"}, new int[] {50}, new String[] {"סוסים צבעוניים שמסתובבים"});
        add("treehouse", "home", new String[] {"בית עץ", "בית עץ עם אומגה"}, new int[] {45, 90},
                new String[] {"בית קטן על העץ וסולם חבלים", "אומגה ונדנדה"});
        add("robot", "fun", new String[] {"רובוט"}, new int[] {70}, new String[] {"רובוט שמנופף שלום"});
        add("ferris", "fun", new String[] {"גלגל ענק"}, new int[] {120}, new String[] {"שמונה תאים צבעוניים שמסתובבים"});
        add("dino", "animals", new String[] {"דינוזאור"}, new int[] {80}, new String[] {"דינוזאור ירוק עם צוואר ארוך"});
        add("helicopter", "transport", new String[] {"מסוק"}, new int[] {160}, new String[] {"ממריא, מרחף ונוחת"});
        add("dragon", "magic", new String[] {"ביצת דרקון", "דרקון קטן", "דרקון מעופף"}, new int[] {60, 140, 300},
                new String[] {"ביצה מנוקדת שזזה", "דרקון ירוק עם כנפיים", "דרקון אדום שעף מעל האי ויורק אש"});
        add("wizard", "magic", new String[] {"מגדל קוסמים", "מגדל קוסמים גבוה"}, new int[] {110, 220},
                new String[] {"כדור קסם שמרחף ומאיר", "מגדל גבוה יותר וכוכבים"});
        add("volcano", "production", new String[] {"הר געש", "הר געש פעיל"}, new int[] {150, 260},
                new String[] {"לבה זוהרת", "עשן שעולה בלי הפסקה"});
        add("unicorn", "magic", new String[] {"חד-קרן", "חד-קרן וקשת"}, new int[] {90, 180},
                new String[] {"רעמה בצבעי הקשת וקרן זהב", "קשת בענן מעליו"});
        add("observatory", "production", new String[] {"מצפה כוכבים"}, new int[] {130}, new String[] {"כיפה שמסתובבת וטלסקופ"});
        add("coaster", "fun", new String[] {"רכבת הרים"}, new int[] {200}, new String[] {"מסילה מתפתלת ושתי קרונות שנוסעות"});
        add("ufo", "transport", new String[] {"צלחת מעופפת"}, new int[] {280}, new String[] {"חייזר ירוק, אורות וקרן"});
    }

    /**
     * 2026-11-13 (Shir: "on every island new things to buy"): from which island (0 = the first) each
     * thing can be bought; everything else from the first. Each island shows its new ones, and what
     * the next island will bring.
     */
    public static final Map<String, Integer> FROM_ISLE = Map.ofEntries(
            Map.entry("school", 1), Map.entry("fountain", 1), Map.entry("cow", 1),
            Map.entry("playground", 2), Map.entry("horse", 2),
            Map.entry("balloon", 3),
            Map.entry("rocket", 5),
            // 2026-11-21: the wonders - something new to wait for on every island
            Map.entry("treehouse", 1), Map.entry("robot", 1),
            Map.entry("ferris", 2), Map.entry("dino", 2), Map.entry("helicopter", 2),
            Map.entry("dragon", 3), Map.entry("wizard", 3),
            Map.entry("volcano", 4), Map.entry("unicorn", 4), Map.entry("observatory", 4),
            Map.entry("coaster", 5),
            Map.entry("ufo", 6));

    public static int fromIsle(String kind) {
        return FROM_ISLE.getOrDefault(kind, 0);
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
            // 2026-11-14 (A5): coins given to the class building - spent, nothing on the island.
            if (it.getKind().startsWith("donate:")) {
                spent += it.getLevel();
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
            c.put("season", k.season());
            c.put("fromIsle", fromIsle(k.key()));
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
        if (k.season() != null && !k.season().equals(seasonOf(isle))) throw bad("זה מתאים רק לאי בעונה אחרת");
        if (isle < fromIsle(kind)) throw bad("זה נפתח רק באי מספר " + (fromIsle(kind) + 1));
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
