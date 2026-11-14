package com.example.brainblocks.cloud;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 2026-11-14 (Shir: "the server must always be awake - something has to wake it"): the free host puts a
 * web service to sleep after 15 minutes without visitors. Every 10 minutes the server calls its own
 * public address (/api/ping), which goes in through the host's front door and counts as a visitor, so
 * it never reaches 15 quiet minutes. Off unless brainblocks.keep-awake-url is set (only in the cloud;
 * the host fills RENDER_EXTERNAL_URL by itself).
 */
@Component
public class KeepAwake {

    private static final Logger log = LoggerFactory.getLogger("brainblocks.keep-awake");
    static final long EVERY_MINUTES = 10;

    private final String url;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    private ScheduledExecutorService timer;
    private int failuresInARow;

    public KeepAwake(@Value("${brainblocks.keep-awake-url:}") String base) {
        this.url = base == null || base.isBlank() ? "" : base.replaceAll("/+$", "") + "/api/ping";
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (url.isEmpty()) return;
        timer = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "keep-awake");
            t.setDaemon(true);
            return t;
        });
        timer.scheduleAtFixedRate(this::ping, 1, EVERY_MINUTES, TimeUnit.MINUTES);
        log.info("keep-awake on: {} every {} min", url, EVERY_MINUTES);
    }

    void ping() {
        try {
            HttpResponse<Void> r = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30)).GET().build(),
                    HttpResponse.BodyHandlers.discarding());
            if (r.statusCode() == 200) {
                if (failuresInARow > 0) log.info("keep-awake ok again after {} failures", failuresInARow);
                failuresInARow = 0;
            } else {
                failuresInARow++;
                log.warn("keep-awake got {}", r.statusCode());
            }
        } catch (Exception e) {
            failuresInARow++;
            log.warn("keep-awake failed: {}", e.toString());
        }
    }

    String url() {
        return url;
    }

    @PreDestroy
    public void stop() {
        if (timer != null) timer.shutdownNow();
    }
}
