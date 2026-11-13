package com.example.brainblocks.learning.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 2026-11-13 (Shir: security - "alerts to my phone"): something suspicious on the site -> a notification
 * on her phone, through Katrine's local endpoint (loopback only, nothing leaves the PC; Katrine pushes it
 * over her own channel). At most one per kind every 10 minutes. Off unless brainblocks.phone-alerts=on
 * (local.properties) - so tests never send real alerts. A failure is only logged.
 */
@Service
public class PhoneAlerts {

    private static final Logger log = LoggerFactory.getLogger("brainblocks.alerts");
    private static final long QUIET_MS = 10 * 60_000L;

    private final boolean on;
    private final String url;
    private final Map<String, Long> last = new ConcurrentHashMap<>();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    public PhoneAlerts(@Value("${brainblocks.phone-alerts:off}") String mode,
                       @Value("${brainblocks.phone-alerts-url:http://127.0.0.1:8081/local/phone-alert}") String url) {
        this.on = "on".equalsIgnoreCase(mode.trim());
        this.url = url;
    }

    /** kind: for the 10-minute quiet time (e.g. "login-lock"); text: what she reads. */
    public void send(String kind, String text) {
        log.warn("alert [{}] {}", kind, text);
        if (!on) return;
        long now = System.currentTimeMillis();
        Long prev = last.get(kind);
        if (prev != null && now - prev < QUIET_MS) return;
        last.put(kind, now);
        String body = "{\"title\":\"BrainBlocks - אבטחה\",\"text\":" + json(text) + "}";
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(4))
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(body, java.nio.charset.StandardCharsets.UTF_8)).build();
        http.sendAsync(req, HttpResponse.BodyHandlers.discarding())
                .whenComplete((r, e) -> {
                    if (e != null) log.info("phone alert not sent ({}): {}", kind, e.getMessage());
                });
    }

    static String json(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                default -> {
                    if (c < 0x20) b.append(' ');
                    else b.append(c);
                }
            }
        }
        return b.append('"').toString();
    }
}
