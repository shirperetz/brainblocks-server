package com.example.brainblocks.cloud;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 2026-11-14 (Shir: "the server must always be awake"): a tiny answer for the waker and the host's
 * health check. It never touches the database, so pinging it every few minutes doesn't keep the free
 * database (Neon) awake and doesn't use its free hours.
 */
@RestController
public class PingController {
    @GetMapping("/api/ping")
    public String ping() {
        return "ok";
    }
}
