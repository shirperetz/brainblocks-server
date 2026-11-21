package com.example.brainblocks.learning.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.brainblocks.learning.model.Avatar;
import com.example.brainblocks.learning.model.LearnIslandItem;
import com.example.brainblocks.learning.model.LearnerProfile;
import com.example.brainblocks.learning.repository.LearnIslandItemRepository;
import com.example.brainblocks.learning.repository.LearnerProfileRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** 2026-11-21 (security pass): six taps at the same moment with coins for one item buy one item. */
@SpringBootTest(properties = {
        "DATABASE_URL=jdbc:h2:mem:spend;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "DATABASE_USER=sa", "DATABASE_PASSWORD=", "JOIN_CODE=4711", "ADMIN_USERNAMES=boss", "PUBLIC_ORIGIN=https://example.org"})
@ActiveProfiles("cloud")
class DoubleSpendTest {
    @Autowired IslandShopService shop;
    @Autowired LearnerProfileRepository profiles;
    @Autowired LearnIslandItemRepository items;

    @Test
    void parallelBuysCannotSpendTheSameCoinsTwice() throws Exception {
        Long id = profiles.save(new LearnerProfile("kid", Avatar.values()[0])).getId();
        items.save(new LearnIslandItem(id, "bonus", -1, 10)); // exactly one tent (10)
        ExecutorService pool = Executors.newFixedThreadPool(6);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Boolean>> tries = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            int slot = i % 3;
            tries.add(pool.submit(() -> {
                go.await();
                try {
                    shop.build(id, 0, slot, "house");
                    return true;
                } catch (RuntimeException e) {
                    return false;
                }
            }));
        }
        go.countDown();
        int ok = 0;
        for (Future<Boolean> f : tries) if (f.get(30, TimeUnit.SECONDS)) ok++;
        pool.shutdown();
        assertThat(ok).isEqualTo(1);
        assertThat(((Number) shop.state(id, 0).get("coins")).intValue()).isZero();
    }
}
