package com.example.brainblocks.learning.controller;

import com.example.brainblocks.config.LearnerAuthConfig;
import com.example.brainblocks.learning.service.LiveRoomService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 2026-11-12: the live game room. The teacher (or the admin): open, see live, approve, start, +1 minute,
 * end, the board, her own exercises. A logged-in child: join by code, its state, a question, an answer.
 * Guests (no account) use {@link RoomGuestController}.
 */
@RestController
@RequestMapping("/api/learn/rooms")
public class RoomController {

    private final LiveRoomService rooms;

    public RoomController(LiveRoomService rooms) {
        this.rooms = rooms;
    }

    // ---- the teacher ----

    /** body: { classId?, topics: ["FRACTIONS", "MY"...], minutes, grade? (no class) } */
    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body, @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        Object t = body.get("topics");
        // 2026-11-22: coins for the island (#5) and two groups (#9) - the teacher's choice
        return rooms.create(me, body.get("classId"), t instanceof List<?> l ? l : List.of(), body.get("minutes"), body.get("grade"),
                Boolean.TRUE.equals(body.get("coins")), Boolean.TRUE.equals(body.get("teams")));
    }

    // ---- 2026-11-22: saved rooms (#10) and the warm-up before a room ----

    @GetMapping("/templates")
    public List<Map<String, Object>> templates(@RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        return rooms.templates(me);
    }

    /** body: { name, classId?, grade?, topics, minutes, coins?, teams? } */
    @PostMapping("/templates")
    public Map<String, Object> saveTemplate(@RequestBody Map<String, Object> body, @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        return rooms.saveTemplate(me, body);
    }

    @PostMapping("/templates/{tid}/delete")
    public Map<String, Object> deleteTemplate(@PathVariable long tid, @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        rooms.deleteTemplate(me, tid);
        return Map.of("ok", true);
    }

    @PostMapping("/templates/{tid}/open")
    public Map<String, Object> openTemplate(@PathVariable long tid, @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        return rooms.openTemplate(me, tid);
    }

    /** The teacher opened "new room": wake the question code up in the background (staff only, answers at once). */
    @PostMapping("/warmup")
    public Map<String, Object> warmup(@RequestBody(required = false) Map<String, Object> body, @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        rooms.templates(me); // staff check
        Object g = body == null ? null : body.get("grade");
        com.example.brainblocks.learning.model.Grade grade;
        try {
            grade = g == null ? null : com.example.brainblocks.learning.model.Grade.valueOf(g.toString());
        } catch (IllegalArgumentException e) {
            grade = null;
        }
        final com.example.brainblocks.learning.model.Grade target = grade;
        java.util.concurrent.CompletableFuture.runAsync(() -> rooms.warmUp(target));
        return Map.of("ok", true);
    }

    @GetMapping("/{id}/live")
    public Map<String, Object> live(@PathVariable long id, @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        return rooms.live(me, id);
    }

    @GetMapping("/{id}/board")
    public Map<String, Object> board(@PathVariable long id, @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        return rooms.board(me, id);
    }

    @PostMapping("/{id}/players/{pid}/approve")
    public Map<String, Object> approve(@PathVariable long id, @PathVariable long pid, @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        rooms.decide(me, id, pid, true);
        return Map.of("ok", true);
    }

    @PostMapping("/{id}/players/{pid}/reject")
    public Map<String, Object> reject(@PathVariable long id, @PathVariable long pid, @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        rooms.decide(me, id, pid, false);
        return Map.of("ok", true);
    }

    @PostMapping("/{id}/start")
    public Map<String, Object> start(@PathVariable long id, @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        rooms.start(me, id);
        return rooms.live(me, id);
    }

    @PostMapping("/{id}/extend")
    public Map<String, Object> extend(@PathVariable long id, @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        rooms.extend(me, id);
        return rooms.live(me, id);
    }

    @PostMapping("/{id}/pause")
    public Map<String, Object> pause(@PathVariable long id, @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        rooms.pause(me, id);
        return rooms.live(me, id);
    }

    @PostMapping("/{id}/resume")
    public Map<String, Object> resume(@PathVariable long id, @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        rooms.resume(me, id);
        return rooms.live(me, id);
    }

    @PostMapping("/{id}/end")
    public Map<String, Object> end(@PathVariable long id, @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        rooms.end(me, id);
        return rooms.live(me, id);
    }

    @GetMapping("/my-questions")
    public List<Map<String, Object>> myQuestions(@RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        return rooms.myQuestions(me);
    }

    /** body: { text, correct, wrong1?, wrong2?, wrong3? } */
    @PostMapping("/my-questions")
    public Map<String, Object> addQuestion(@RequestBody Map<String, Object> body, @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        return rooms.addQuestion(me, body);
    }

    @PostMapping("/my-questions/{qid}/off")
    public Map<String, Object> questionOff(@PathVariable long qid, @RequestBody Map<String, Object> body,
                                           @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        rooms.setQuestionOff(me, qid, Boolean.parseBoolean(String.valueOf(body.get("off"))));
        return Map.of("ok", true);
    }

    // ---- a logged-in child ----

    @PostMapping("/join")
    public Map<String, Object> join(@RequestBody Map<String, Object> body, @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        return rooms.joinChild(me, body.get("code"));
    }

    @GetMapping("/code/{code}/me")
    public Map<String, Object> me(@PathVariable String code, @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        return rooms.stateForChild(me, code);
    }

    @GetMapping("/code/{code}/question")
    public Map<String, Object> question(@PathVariable String code, @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        return rooms.questionForChild(me, code);
    }

    /** body: { answer: "the option's text" } */
    @PostMapping("/code/{code}/answer")
    public Map<String, Object> answer(@PathVariable String code, @RequestBody Map<String, Object> body,
                                      @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        return rooms.answerForChild(me, code, body.get("answer"));
    }
}
