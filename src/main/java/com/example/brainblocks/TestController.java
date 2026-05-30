package com.example.brainblocks;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@CrossOrigin(origins = "http://localhost:5173")
public class TestController {

    @GetMapping("/api/hello")
    public String hello() {
        return "BrainBlocks server and client are connected!";
    }
    @GetMapping("/api/check-sync")
    public Map<String, Object> checkSync() {
        return Map.of(
                "project", "BrainBlocks",
                "connected", true,
                "message", "Server and client are synchronized",
                "serverTime", LocalDateTime.now().toString(),
                "randomNumber", Math.random()
        );
    }
}