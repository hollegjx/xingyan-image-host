package learn.gjx.tuku1.application.collaboration;

import learn.gjx.tuku1.domain.collaboration.EditRoom;
import learn.gjx.tuku1.model.entity.Picture;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CollaborationServiceTest {
    final Set<String> denied = new HashSet<>();
    final Picture picture = picture();
    final CollaborationService service = new CollaborationService((sid, uid, pid) -> {
        if (denied.contains(sid)) throw new IllegalStateException("denied");
        return new CollaborationAccess.Actor(uid, "name", picture);
    });
    static Picture picture() { Picture p = new Picture(); p.setId(10L); p.setUrl("private/secret.png"); return p; }
    class Client implements CollaborationService.Connection {
        final String id; final Long user; final Long pic; EditRoom.Snapshot state; String type; boolean closed; int received;
        Client(String id, long user, long pic) { this.id = id; this.user = user; this.pic = pic; }
        public String id() { return id; } public String sessionId() { return id; }
        public Long userId() { return user; } public Long pictureId() { return pic; }
        public void send(String type, EditRoom.Snapshot state, String message) { this.type = type; this.state = state; received++; }
        public void close() { closed = true; }
    }
    @Test void syncLateJoinAndKeepTokensPrivate() {
        Client a = new Client("a", 1, 10); service.connect(a); service.message("a", "ENTER_EDIT", null, null);
        service.message("a", "EDIT_ACTION", "ZOOM_IN", 1L);
        Client b = new Client("b", 2, 10); service.connect(b);
        assertEquals(1, b.state.zoom); assertNull(b.state.leaseToken); assertNotNull(a.state.leaseToken);
        service.message("b", "EDIT_ACTION", "ZOOM_IN", 2L); assertEquals("ERROR", b.type);
        assertFalse(b.state.sourceVersion.contains("secret"));
    }
    @Test void revokeBeforeBroadcastAndReleaseOwner() {
        Client a = new Client("a", 1, 10), b = new Client("b", 2, 10); service.connect(a); service.connect(b);
        service.message("a", "ENTER_EDIT", null, null); int received = a.received; denied.add("a"); service.tick();
        assertTrue(a.closed); assertEquals(received, a.received); assertNull(b.state.editorId);
        service.message("b", "ENTER_EDIT", null, null); assertTrue(b.state.editing);
    }
    @Test void wrongRoomNeverReceivesBroadcast() {
        Client a = new Client("a", 1, 10), b = new Client("b", 2, 20); service.connect(a); service.connect(b);
        int received = b.received; service.message("a", "ENTER_EDIT", null, null); assertEquals(received, b.received);
    }
    @Test void lastDisconnectDiscardsUnsavedRoomAndOldToken() {
        Client a = new Client("a", 1, 10); service.connect(a); service.message("a", "ENTER_EDIT", null, null);
        String token = a.state.leaseToken; service.disconnect("a");
        assertThrows(IllegalStateException.class, () -> service.beginWrite(picture, 1L, token, 1L));
        Client b = new Client("b", 2, 10); service.connect(b); assertNull(b.state.editorId);
    }
    @Test void limitConnectionsPerUser() {
        for (int i = 0; i < 4; i++) service.connect(new Client("a" + i, 1, 10));
        Client fifth = new Client("a4", 1, 10); service.connect(fifth); assertTrue(fifth.closed);
    }
    @Test void httpWriteBlocksSocketAcquireUntilCommit() {
        EditRoom guard = service.beginWrite(picture, 1L, null, null);
        Client c = new Client("c", 2, 10); service.connect(c); assertTrue(c.state.saving);
        service.message("c", "ENTER_EDIT", null, null); assertEquals("ERROR", c.type);
        service.finishWrite(10L, guard, picture); service.changed(10L);
        service.message("c", "ENTER_EDIT", null, null); assertTrue(c.state.editing);
    }
    @Test void saveRevalidatesOwnerSessionWithoutWaitingForSweep() {
        Client a = new Client("a", 1, 10); service.connect(a); service.message("a", "ENTER_EDIT", null, null);
        String token = a.state.leaseToken; denied.add("a");
        assertThrows(IllegalStateException.class, () -> service.beginWrite(picture, 1L, token, 1L));
        assertTrue(a.closed);
    }
}
