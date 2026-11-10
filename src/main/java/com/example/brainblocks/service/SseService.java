package com.example.brainblocks.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class SseService {

    private final Map<String, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public SseService() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public SseEmitter subscribe(String roomCode) {
        SseEmitter emitter = new SseEmitter(0L);
        String key = roomCode.toUpperCase();
        emitters.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(emitter);

        Runnable cleanup = () -> {
            CopyOnWriteArrayList<SseEmitter> list = emitters.get(key);
            if (list != null) list.remove(emitter);
        };
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());

        try {
            emitter.send(SseEmitter.event().name("CONNECTED").data("{\"connected\":true}"));
        } catch (Exception ignored) {}

        return emitter;
    }

    public void broadcast(String roomCode, String eventName, Object data) {
        List<SseEmitter> roomEmitters = emitters.get(roomCode.toUpperCase());
        if (roomEmitters == null || roomEmitters.isEmpty()) return;

        String json;
        try {
            json = objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            json = "{}";
        }

        String finalJson = json;
        List<SseEmitter> dead = new java.util.ArrayList<>();
        for (SseEmitter emitter : roomEmitters) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(finalJson));
            } catch (Exception ex) {
                dead.add(emitter);
            }
        }
        roomEmitters.removeAll(dead);
    }
}
