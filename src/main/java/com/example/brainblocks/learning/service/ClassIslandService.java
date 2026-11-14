package com.example.brainblocks.learning.service;

import com.example.brainblocks.learning.model.LearnClass;
import com.example.brainblocks.learning.model.LearnClassWeek;
import com.example.brainblocks.learning.model.LearnerProfile;
import com.example.brainblocks.learning.repository.LearnClassRepository;
import com.example.brainblocks.learning.repository.LearnClassWeekRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 2026-11-14 (Shir: "a class island - in 3D like the children's islands, upgraded"; chose direction A):
 * every week the class completes its building, a new building stays on the class's 3D island for the
 * whole year; after a full round the older ones grow a level. This week's building stands on the island
 * as a construction site. And a league between classes: bricks per child this week (so a small class
 * competes fairly) - classes only, never children.
 */
@Service
public class ClassIslandService {

    /** The class island's buildings, in the order the weeks earn them (drawn by IslandScene). */
    public static final List<String> SEQUENCE = List.of(
            "school", "library", "fountain", "playground", "lighthouse", "clocktower", "stadium", "statue", "pond", "balloon");
    public static final Map<String, String> NAMES = Map.of(
            "school", "בית ספר", "library", "ספרייה", "fountain", "מזרקה", "playground", "גן שעשועים", "lighthouse", "מגדלור",
            "clocktower", "מגדל שעון", "stadium", "אצטדיון", "statue", "פסל הכיתה", "pond", "אגם", "balloon", "כדור פורח");
    public static final int MAX_LEVEL = 3;
    private static final int MAX_WEEKS = 60;
    private static final int LEAGUE_SIZE = 10;

    private final LearnClassRepository classes;
    private final LearnClassWeekRepository weeks;
    private final ClassBuildingService building;
    private final AccessService access;

    public ClassIslandService(LearnClassRepository classes, LearnClassWeekRepository weeks, ClassBuildingService building,
                              AccessService access) {
        this.classes = classes;
        this.weeks = weeks;
        this.building = building;
        this.access = access;
    }

    /** Buildings for [done] finished weeks: one per week in SEQUENCE order; a new round levels the older ones up. */
    static List<Map<String, Object>> buildings(int done) {
        List<Map<String, Object>> out = new ArrayList<>();
        int n = SEQUENCE.size();
        for (int j = 0; j < Math.min(done, n); j++) {
            Map<String, Object> b = new LinkedHashMap<>();
            b.put("slot", j);
            b.put("kind", SEQUENCE.get(j));
            b.put("level", Math.min(MAX_LEVEL, 1 + (done - 1 - j) / n));
            b.put("name", NAMES.get(SEQUENCE.get(j)));
            out.add(b);
        }
        return out;
    }

    /** What the class builds this week (the next new one, or the next one to grow after a full round). */
    static Map<String, Object> next(int done) {
        int n = SEQUENCE.size();
        String kind = SEQUENCE.get(done % n);
        int level = Math.min(MAX_LEVEL, 1 + done / n);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("kind", kind);
        m.put("name", NAMES.get(kind));
        m.put("level", level);
        m.put("upgrade", done >= n);
        return m;
    }

    /** The class's finished weeks - each past week counted once and kept. */
    private List<LearnClassWeek> history(LearnClass c) {
        List<LearnClassWeek> known = weeks.findByClassIdOrderByWeekStartAsc(c.getId());
        Set<LocalDate> have = known.stream().map(LearnClassWeek::getWeekStart).collect(Collectors.toSet());
        LocalDateTime thisWeek = ClassBuildingService.weekStart(LocalDate.now());
        LocalDateTime from = ClassBuildingService.weekStart((c.getCreatedAt() == null ? LocalDateTime.now() : c.getCreatedAt()).toLocalDate());
        if (from.isBefore(thisWeek.minusWeeks(MAX_WEEKS))) from = thisWeek.minusWeeks(MAX_WEEKS);
        boolean added = false;
        for (LocalDateTime w = from; w.isBefore(thisWeek); w = w.plusWeeks(1)) {
            if (have.contains(w.toLocalDate())) continue;
            Map<String, Object> m = building.pastWeek(c, w);
            try {
                weeks.save(new LearnClassWeek(c.getId(), w.toLocalDate(), ((Number) m.get("built")).intValue(),
                        ((Number) m.get("target")).intValue(), Boolean.TRUE.equals(m.get("done"))));
                added = true;
            } catch (RuntimeException ignored) {
                // another request recorded the same week at the same moment - fine
            }
        }
        return added ? weeks.findByClassIdOrderByWeekStartAsc(c.getId()) : known;
    }

    private Map<String, Object> state(LearnClass c, Long childId) {
        List<LearnClassWeek> past = history(c);
        int done = (int) past.stream().filter(LearnClassWeek::isDone).count();
        Map<String, Object> now = building.week(c, childId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("classId", c.getId());
        out.put("className", c.getName());
        out.put("weeksDone", done);
        out.put("weeksTotal", past.size());
        out.put("buildings", buildings(done));
        out.put("next", next(done));
        out.put("week", now);
        out.put("league", league(c.getId()));
        return out;
    }

    /** Every open class this week by bricks per child (one decimal); the asking class is marked. */
    List<Map<String, Object>> league(Long mine) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (LearnClass c : classes.findAll()) {
            if (c.isClosed()) continue;
            Map<String, Object> w = building.week(c, null);
            int kids = ((Number) w.get("children")).intValue();
            if (kids == 0) continue;
            Map<String, Object> r = new HashMap<>();
            r.put("name", c.getName());
            r.put("perChild", Math.round(((Number) w.get("built")).doubleValue() / kids * 10) / 10.0);
            r.put("done", w.get("done"));
            r.put("mine", c.getId().equals(mine));
            rows.add(r);
        }
        rows.sort((a, b) -> Double.compare((Double) b.get("perChild"), (Double) a.get("perChild")));
        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> r = rows.get(i);
            r.put("place", i + 1);
            if (i < LEAGUE_SIZE || Boolean.TRUE.equals(r.get("mine"))) out.add(r);
        }
        return out;
    }

    /** The child's class island ({} without a class). */
    public Map<String, Object> forChild(LearnerProfile child) {
        if (child.getClassId() == null) return Map.of();
        return classes.findById(child.getClassId()).filter(c -> !c.isClosed()).map(c -> state(c, child.getId())).orElse(Map.of());
    }

    /** A teacher's class island (or any class, for the admin). */
    public Map<String, Object> forStaff(Long me, Long classId) {
        LearnerProfile v = access.requireStaff(me);
        LearnClass c = classes.findById(classId == null ? -1L : classId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "אין כיתה כזו"));
        if (access.role(v) != AccessService.Role.ADMIN && !c.getTeacherId().equals(v.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "זו לא כיתה שלך");
        }
        return state(c, null);
    }
}
