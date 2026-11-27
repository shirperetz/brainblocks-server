package com.example.brainblocks.learning.controller;

import com.example.brainblocks.config.LearnerAuthConfig;
import com.example.brainblocks.learning.model.LearnIslandItem;
import com.example.brainblocks.learning.repository.LearnIslandItemRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * 2026-11-27 (Shir: idea #7 - ship voyages). The ship the child built on an island sails to a place;
 * the voyage itself is a round of mixed exercises (played on the client). Finishing well brings back
 * coins - once per destination per day. Needs a ship on the island first.
 */
@RestController
public class VoyageController {

    /** key, Hebrew name, emoji, coins. Fixed list. */
    static final List<String[]> DESTS = List.of(
            new String[] {"reef", "אי האלמוגים", "🏝️", "10"},
            new String[] {"pearl", "מערת הפנינים", "🐚", "12"},
            new String[] {"ice", "הר הקרח", "🗻", "15"},
            new String[] {"volcano", "הר הגעש", "🌋", "20"});

    private final LearnIslandItemRepository islandItems;

    public VoyageController(LearnIslandItemRepository islandItems) {
        this.islandItems = islandItems;
    }

    @GetMapping("/api/learn/profiles/{id}/voyages")
    public Map<String, Object> list(@PathVariable Long id, @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        own(id, me);
        var mine = islandItems.findByProfileId(id);
        boolean hasShip = mine.stream().anyMatch(it -> "ship".equals(it.getKind()));
        String today = LocalDate.now().toString();
        List<Map<String, Object>> dests = new ArrayList<>();
        for (String[] d : DESTS) {
            boolean doneToday = mine.stream().anyMatch(it -> it.getKind().equals("voyage:" + d[0] + ":" + today));
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", d[0]);
            m.put("name", d[1]);
            m.put("emoji", d[2]);
            m.put("coins", Integer.parseInt(d[3]));
            m.put("doneToday", doneToday);
            dests.add(m);
        }
        return Map.of("hasShip", hasShip, "destinations", dests);
    }

    /** After the voyage round (client checks it was passed): coins for the island, once per place per day. */
    @PostMapping("/api/learn/profiles/{id}/voyages/{key}/reward")
    @Transactional
    public Map<String, Object> reward(@PathVariable Long id, @PathVariable String key,
                                      @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        own(id, me);
        String[] dest = DESTS.stream().filter(d -> d[0].equals(key)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "אין מסע כזה"));
        var mine = islandItems.findByProfileId(id);
        if (!mine.stream().anyMatch(it -> "ship".equals(it.getKind()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "צריך ספינה באי כדי להפליג");
        }
        String rowKind = "voyage:" + key + ":" + LocalDate.now();
        if (mine.stream().anyMatch(it -> it.getKind().equals(rowKind))) {
            return Map.of("coins", 0, "already", true);
        }
        int coins = Integer.parseInt(dest[3]);
        islandItems.save(new LearnIslandItem(id, rowKind, 0, coins));
        return Map.of("coins", coins, "already", false, "name", dest[1]);
    }

    private static void own(Long id, Long me) {
        if (!id.equals(me)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found");
    }
}
