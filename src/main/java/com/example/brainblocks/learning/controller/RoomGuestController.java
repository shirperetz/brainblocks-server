package com.example.brainblocks.learning.controller;

import com.example.brainblocks.learning.service.LiveRoomService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 2026-11-12 (Shir chose: a child without an account may join with a name - the teacher approves): the
 * guest's side of a room. No login: the guest gets a random token for THIS room only (header
 * X-Guest-Token), sees nothing but its own game, and nothing of it is saved after the room.
 */
@RestController
@RequestMapping("/api/learn/room-guest")
public class RoomGuestController {

    public static final String HEADER = "X-Guest-Token";

    private final LiveRoomService rooms;

    public RoomGuestController(LiveRoomService rooms) {
        this.rooms = rooms;
    }

    /** body: { code, name } -> { ..., guestToken } (waits for the teacher's OK). */
    @PostMapping("/join")
    public Map<String, Object> join(@RequestBody Map<String, Object> body) {
        return rooms.joinGuest(body.get("code"), body.get("name"));
    }

    @GetMapping("/{code}/me")
    public Map<String, Object> me(@PathVariable String code, @RequestHeader(value = HEADER, required = false) String token) {
        return rooms.stateForGuest(code, token);
    }

    @GetMapping("/{code}/question")
    public Map<String, Object> question(@PathVariable String code, @RequestHeader(value = HEADER, required = false) String token) {
        return rooms.questionForGuest(code, token);
    }

    @PostMapping("/{code}/answer")
    public Map<String, Object> answer(@PathVariable String code, @RequestBody Map<String, Object> body,
                                      @RequestHeader(value = HEADER, required = false) String token) {
        return rooms.answerForGuest(code, token, body.get("answer"));
    }
}
