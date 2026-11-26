package com.example.brainblocks.learning.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** 2026-11-26: a round sent again (slow answer, retry) is saved once; no key = every call saves. */
class RoundOnceTest {

    @Test
    void sameKeySavesOnceEvenAtTheSameTime() throws Exception {
        RoundOnce.clearForTests();
        AtomicInteger saves = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(6);
        var futures = new java.util.ArrayList<Future<Integer>>();
        for (int i = 0; i < 6; i++) futures.add(pool.submit(() -> RoundOnce.run(7L, "abc", () -> { sleep(); return saves.incrementAndGet(); })));
        for (var f : futures) assertEquals(1, f.get());
        pool.shutdown();
        assertEquals(1, saves.get());
        // another child with the same key is a different round
        assertEquals(2, (int) RoundOnce.run(8L, "abc", saves::incrementAndGet));
    }

    @Test
    void noKeyAlwaysSaves() {
        RoundOnce.clearForTests();
        AtomicInteger saves = new AtomicInteger();
        RoundOnce.run(7L, null, saves::incrementAndGet);
        RoundOnce.run(7L, "", saves::incrementAndGet);
        assertEquals(2, saves.get());
    }

    private static void sleep() { try { Thread.sleep(30); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } }
}
