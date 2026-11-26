package com.example.brainblocks.learning.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 2026-11-26 (Shir: an iPad stuck on "בודקים את התשובות"): the phone now gives up on a slow save
 * and sends it again. A round carries a key the phone made up once; the same key within 30 minutes
 * returns the first answer instead of saving the round twice (and two at once wait for each other).
 */
public final class RoundOnce {

    private static final long KEEP_MS = 30 * 60 * 1000L;
    private record Done(Object value, long at) {}
    private static final Map<String, Done> DONE = new ConcurrentHashMap<>();
    private static final Map<String, Object> LOCKS = new ConcurrentHashMap<>();

    private RoundOnce() {}

    @SuppressWarnings("unchecked")
    public static <T> T run(Long profileId, String roundKey, Supplier<T> save) {
        if (roundKey == null || roundKey.isBlank() || roundKey.length() > 64) return save.get();
        String key = profileId + ":" + roundKey;
        Object lock = LOCKS.computeIfAbsent(key, k -> new Object());
        synchronized (lock) {
            long now = System.currentTimeMillis();
            Done d = DONE.get(key);
            if (d != null && now - d.at() < KEEP_MS) return (T) d.value();
            T value = save.get();
            DONE.put(key, new Done(value, now));
            if (DONE.size() > 5000) DONE.entrySet().removeIf(e -> now - e.getValue().at() >= KEEP_MS);
            if (LOCKS.size() > 5000) LOCKS.keySet().retainAll(DONE.keySet());
            return value;
        }
    }

    static void clearForTests() { DONE.clear(); LOCKS.clear(); }
}
