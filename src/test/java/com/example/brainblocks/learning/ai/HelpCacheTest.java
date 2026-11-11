package com.example.brainblocks.learning.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.brainblocks.learning.model.HelpCacheEntry;
import com.example.brainblocks.learning.repository.HelpCacheRepository;
import com.sun.net.httpserver.HttpServer;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * 2026-11-11 (Shir: "remember the exercise and the help of לא הבנתי, so the AI isn't used again and
 * again"): the same exercise + wrong answer + press + grade asks the model ONCE; after that the saved
 * (already checked) explanation comes back with no model call. A different press or wrong answer is new.
 */
class HelpCacheTest {

    @Test
    void theSameLoHevantiAsksTheModelOnlyOnce() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer model = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        model.createContext("/api/chat", ex -> {
            calls.incrementAndGet();
            byte[] body = "{\"message\":{\"content\":\"נסו לספור קודם 3 אצבעות, ואז עוד 4 אצבעות. כמה יש ביחד?\"}}"
                    .getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(200, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        model.start();
        try {
            HelpService help = new HelpService(true, "http://127.0.0.1:" + model.getAddress().getPort(), "x");
            List<HelpCacheEntry> saved = new ArrayList<>();
            help.setCache(memory(saved));
            String q = "בגינה יש 3 פרחים אדומים ו-4 צהובים, כמה פרחים בסך הכל?";

            HelpService.Help first = help.explain(q, "7", "6", "G1", 2, "");
            assertThat(first.source()).isEqualTo("ai");
            assertThat(calls.get()).isEqualTo(1);
            assertThat(saved).hasSize(1);

            for (int i = 0; i < 5; i++) {
                HelpService.Help again = help.explain(q, "7", "6", "G1", 2, "");
                assertThat(again.source()).isEqualTo("cache");
                assertThat(again.text()).isEqualTo(first.text());
            }
            assertThat(calls.get()).as("no more model calls for the same help").isEqualTo(1);
            assertThat(saved.get(0).getHits()).isEqualTo(5);

            // another wrong answer / another press = a new explanation (asked once, then saved too)
            help.explain(q, "7", "8", "G1", 2, "");
            help.explain(q, "7", "6", "G1", 3, "");
            assertThat(calls.get()).isEqualTo(3);
            assertThat(saved).hasSize(3);
            help.explain(q, "7", "8", "G1", 2, "");
            assertThat(calls.get()).isEqualTo(3);
        } finally {
            model.stop(0);
        }
    }

    @Test
    void theFirstTwoPressesNeverUseTheModelOrTheCache() {
        HelpService help = new HelpService(true, "http://127.0.0.1:1", "x");
        List<HelpCacheEntry> saved = new ArrayList<>();
        help.setCache(memory(saved));
        assertThat(help.explain("כמה זה 7 + 5?", "12", "11", "G1", 0, "").source()).isEqualTo("tutor");
        assertThat(help.explain("כמה זה 7 + 5?", "12", "11", "G1", 1, "").source()).isEqualTo("tutor");
        assertThat(saved).isEmpty();
    }

    /** An in-memory HelpCacheRepository - only the two calls HelpService makes. */
    private static HelpCacheRepository memory(List<HelpCacheEntry> rows) {
        return (HelpCacheRepository) Proxy.newProxyInstance(HelpCacheRepository.class.getClassLoader(),
                new Class<?>[] {HelpCacheRepository.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "findByCacheKey" -> rows.stream().filter(r -> r.getCacheKey().equals(args[0])).findFirst();
                    case "save" -> {
                        HelpCacheEntry e = (HelpCacheEntry) args[0];
                        if (!rows.contains(e)) rows.add(e);
                        yield e;
                    }
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "memory cache";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    @SuppressWarnings("unused")
    private static Optional<HelpCacheEntry> none() {
        return Optional.empty();
    }
}
