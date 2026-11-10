package com.example.brainblocks.learning.ai;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Reads a help text aloud for children who can't read yet (grades א-ב). Optional: a local TTS
 * worker that takes UTF-8 text and returns a WAV (brainblocks.tts.url). Without one, the
 * explanation is shown as text only.
 */
@Service
public class SpeechService {

    private final String url;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    public SpeechService(@Value("${brainblocks.tts.url:http://127.0.0.1:8098/synthesize}") String url) {
        this.url = url;
    }

    /** WAV bytes, or null when no speech is available. */
    public byte[] speak(String text) {
        if (url == null || url.isBlank() || text == null || text.isBlank()) return null;
        String t = text.length() > 400 ? text.substring(0, 400) : text;
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "text/plain; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(t, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<byte[]> res = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
            return res.statusCode() == 200 && res.body().length > 44 ? res.body() : null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
