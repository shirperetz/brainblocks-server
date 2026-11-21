package com.example.brainblocks.learning.service;

import com.example.brainblocks.learning.dto.request.AnswerDto;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * 2026-11-21 (Shir: "do all the security checks"): the island games run on the phone, so the server
 * can't re-solve them. A result that could not have come from a child playing is refused:
 * <ul>
 *   <li>"right" only when the chosen answer equals the right answer (the phone's flag is ignored);</li>
 *   <li>a round has at least {@link #MIN_ANSWERS} answers (one answer = 100% = 3 stars);</li>
 *   <li>at least {@link #MS_PER_ANSWER} ms per answer since the child's previous game result,
 *       measured on the SERVER clock (the phone's durationMs can be faked). Real rounds take far longer.</li>
 * </ul>
 * And for question topics: an answer with no question of the child's own in the bank is wrong,
 * and the same bank question counts once per round.
 */
public final class GameResultGuard {
    public static final int MIN_ANSWERS = 5;
    public static final long MS_PER_ANSWER = 2000;

    private static final Map<Long, Long> LAST_GAME = new ConcurrentHashMap<>();
    static LongSupplier clock = System::currentTimeMillis;

    private GameResultGuard() {}

    public static void checkGame(Long profileId, List<AnswerDto> answers) {
        if (answers.size() < MIN_ANSWERS) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "סבב קצר מדי");
        for (AnswerDto a : answers) {
            a.setCorrect(a.getChosenAnswer() != null && a.getCorrectAnswer() != null
                    && a.getChosenAnswer().trim().equals(a.getCorrectAnswer().trim()));
        }
        long now = clock.getAsLong();
        Long prev = LAST_GAME.get(profileId);
        if (prev != null && now - prev < answers.size() * MS_PER_ANSWER) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "רגע, לאט לאט 🙂");
        }
        LAST_GAME.put(profileId, now);
    }

    /** Question topics: no own bank row = wrong; one bank question counts once in a round. */
    public static void unverifiedIsWrong(List<AnswerDto> answers, Set<AnswerDto> verified) {
        Set<Long> seen = new HashSet<>();
        for (AnswerDto a : answers) {
            if (!verified.contains(a) || !seen.add(Objects.requireNonNull(a.getBankId()))) a.setCorrect(false);
        }
    }

    public static Set<AnswerDto> newVerifiedSet() {
        return Collections.newSetFromMap(new IdentityHashMap<>());
    }

    static void resetForTests() {
        LAST_GAME.clear();
    }
}
