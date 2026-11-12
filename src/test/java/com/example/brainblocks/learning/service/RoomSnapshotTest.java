package com.example.brainblocks.learning.service;

import com.example.brainblocks.learning.model.Grade;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 2026-11-12: an open game room survives a server restart (a deploy wiped her sister's room). */
class RoomSnapshotTest {

    @TempDir
    Path dir;

    private LiveRoomService service(Path file) {
        LiveRoomService s = new LiveRoomService(null, null, null, null, null, new LearningQuestionService());
        s.useSnapshotFile(file);
        return s;
    }

    @Test
    void aRoomComesBackAfterARestart() throws Exception {
        Path file = dir.resolve("rooms-snapshot.json");
        LiveRoomService before = service(file);
        long now = System.currentTimeMillis();
        LiveRoomService.Room r = new LiveRoomService.Room(7, "123456", 30L, null, "שיר", Grade.G3, List.of("ADDITION"), 300_000, now);
        r.state = LiveRoomService.State.RUNNING;
        r.endsAt = now + 300_000;
        r.nextPlayer = 3;
        r.skills.put("ADD_CARRY", new int[] {4, 3});
        LiveRoomService.Player kid = new LiveRoomService.Player(1, 40L, "רומי", "GIRL", null, Grade.G3);
        kid.approved = true;
        kid.points = 25;
        kid.answered = 4;
        kid.right = 3;
        kid.seen.add("כמה זה 4 + 3?|7");
        LiveRoomService.Player guest = new LiveRoomService.Player(2, null, "אורחת", null, "tok123", Grade.G3);
        r.players.put(1L, kid);
        r.players.put(2L, guest);
        before.putForTest(r);
        before.saveIfChanged();
        assertThat(Files.readString(file)).contains("123456").doesNotContain("\"current\"");

        LiveRoomService after = service(file); // the restart
        after.restore();
        LiveRoomService.Room back = after.roomForTest("123456");
        assertThat(back).isNotNull();
        assertThat(back.state).isEqualTo(LiveRoomService.State.RUNNING);
        assertThat(back.endsAt).isEqualTo(r.endsAt);
        assertThat(back.classId).isNull();
        assertThat(back.skills.get("ADD_CARRY")).containsExactly(4, 3);
        LiveRoomService.Player k = back.players.get(1L);
        assertThat(k.name).isEqualTo("רומי");
        assertThat(k.approved).isTrue();
        assertThat(k.points).isEqualTo(25);
        assertThat(k.seen).containsExactly("כמה זה 4 + 3?|7");
        assertThat(back.players.get(2L).guestToken).isEqualTo("tok123");
        assertThat(back.players.get(2L).approved).isFalse();
    }

    @Test
    void oldRoomsAndBrokenFilesAreIgnored() throws Exception {
        Path file = dir.resolve("rooms-snapshot.json");
        LiveRoomService before = service(file);
        before.putForTest(new LiveRoomService.Room(1, "111111", 30L, null, "x", Grade.G3, List.of("ADDITION"), 60_000,
                System.currentTimeMillis() - LiveRoomService.KEEP_MS - 1000));
        before.putForTest(new LiveRoomService.Room(2, "222222", 30L, null, "y", Grade.G3, List.of("ADDITION"), 60_000,
                System.currentTimeMillis()));
        before.saveIfChanged();
        LiveRoomService after = service(file);
        after.restore();
        assertThat(after.roomForTest("111111")).isNull();
        assertThat(after.roomForTest("222222")).isNotNull();

        Files.writeString(file, "{broken");
        LiveRoomService broken = service(file);
        broken.restore(); // no exception, just nothing
        assertThat(broken.roomForTest("222222")).isNull();
    }
}
