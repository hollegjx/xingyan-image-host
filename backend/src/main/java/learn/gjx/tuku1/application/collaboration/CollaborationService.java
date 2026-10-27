package learn.gjx.tuku1.application.collaboration;

import learn.gjx.tuku1.domain.collaboration.EditRoom;
import learn.gjx.tuku1.model.entity.Picture;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Single-instance application coordinator. Every transition, including HTTP writes, uses this monitor. */
@Service
public class CollaborationService {
    public interface Connection {
        String id(); String sessionId(); Long userId(); Long pictureId();
        void send(String type, EditRoom.Snapshot state, String message) throws Exception;
        void close();
    }
    private final CollaborationAccess access;
    private final Map<String, Connection> connections = new HashMap<>();
    private final Map<Long, EditRoom> rooms = new HashMap<>();
    public CollaborationService(CollaborationAccess access) { this.access = access; }

    public synchronized void connect(Connection c) {
        try {
            CollaborationAccess.Actor actor = authorize(c);
            if (connections.size() >= 128 || connections.values().stream().filter(x -> x.userId().equals(c.userId())).count() >= 4
                    || connections.values().stream().filter(x -> x.pictureId().equals(c.pictureId())).count() >= 16)
                throw new IllegalStateException("协同连接数量已达上限");
            rooms.computeIfAbsent(c.pictureId(), id -> new EditRoom(source(actor.picture))).refreshSource(source(actor.picture));
            connections.put(c.id(), c);
            broadcast(c.pictureId());
        } catch (RuntimeException e) { c.close(); }
    }

    public synchronized void message(String id, String type, String action, Long revision) {
        Connection c = connections.get(id);
        if (c == null) return;
        CollaborationAccess.Actor actor;
        try { actor = authorize(c); } catch (RuntimeException e) { disconnect(id); return; }
        EditRoom room = rooms.get(c.pictureId());
        room.refreshSource(source(actor.picture));
        try {
            long now = System.currentTimeMillis();
            switch (type == null ? "" : type) {
                case "ENTER_EDIT": room.enter(id, c.userId().toString(), actor.name, now); break;
                case "EXIT_EDIT": room.exit(id); break;
                case "EDIT_ACTION":
                    if (revision == null) throw new IllegalStateException("缺少编辑版本");
                    room.action(id, revision, action, now); break;
                case "HEARTBEAT": room.heartbeat(id, now); send(c, "STATE", null); return;
                default: throw new IllegalStateException("不支持的消息类型");
            }
            broadcast(c.pictureId());
        } catch (IllegalStateException | IllegalArgumentException e) { send(c, "ERROR", e.getMessage()); }
    }

    public synchronized void disconnect(String id) {
        Connection c = connections.remove(id);
        if (c == null) return;
        c.close();
        EditRoom room = rooms.get(c.pictureId());
        if (room != null) room.exit(id);
        prune(c.pictureId());
        broadcast(c.pictureId());
    }

    public synchronized void tick() {
        for (Connection c : new ArrayList<>(connections.values())) {
            try { authorize(c); } catch (RuntimeException e) { disconnect(c.id()); }
        }
        for (Long id : new ArrayList<>(rooms.keySet())) {
            EditRoom room = rooms.get(id);
            if (room != null) { room.expire(System.currentTimeMillis()); broadcast(id); }
            prune(id);
        }
    }

    /** Must be called while holding the DB space + picture locks. New connections see writing=true. */
    public synchronized EditRoom beginWrite(Picture current, Long userId, String token, Long revision) {
        EditRoom room = rooms.get(current.getId());
        if (room != null) {
            for (Connection c : new ArrayList<>(connections.values())) {
                if (c.pictureId().equals(current.getId()) && room.snapshot(c.id()).editing) {
                    try { authorize(c); } catch (RuntimeException e) { drop(c); }
                }
            }
        }
        if (room == null && token != null) throw new IllegalStateException("协同编辑已结束，请重新打开编辑器");
        if (room == null) {
            room = new EditRoom(source(current)); rooms.put(current.getId(), room);
        }
        room.beginWrite(userId == null ? "" : userId.toString(), token, revision, source(current), System.currentTimeMillis());
        return room;
    }

    public synchronized void finishWrite(Long id, EditRoom room, Picture committed) {
        room.finishWrite(committed == null ? null : source(committed));
        if (connections.values().stream().noneMatch(c -> c.pictureId().equals(id))) {
            rooms.remove(id, room);
        }
    }

    public synchronized void changed(Long id) { broadcast(id); }
    private CollaborationAccess.Actor authorize(Connection c) { return access.authorize(c.sessionId(), c.userId(), c.pictureId()); }
    private void broadcast(Long id) {
        for (Connection c : new ArrayList<>(connections.values())) {
            if (!c.pictureId().equals(id) || !connections.containsKey(c.id())) continue;
            try {
                CollaborationAccess.Actor actor = authorize(c);
                EditRoom room = rooms.get(id);
                if (room != null) room.refreshSource(source(actor.picture));
                send(c, "STATE", null);
            } catch (RuntimeException e) { drop(c); }
        }
        prune(id);
    }
    private void send(Connection c, String type, String message) {
        EditRoom room = rooms.get(c.pictureId());
        try { if (room != null) c.send(type, room.snapshot(c.id()), message); }
        catch (Exception e) { drop(c); }
    }
    private void drop(Connection c) {
        connections.remove(c.id()); c.close();
        EditRoom room = rooms.get(c.pictureId());
        if (room != null) room.exit(c.id());
    }
    private void prune(Long id) {
        EditRoom room = rooms.get(id);
        if (room != null && !room.isWriting() && connections.values().stream().noneMatch(c -> c.pictureId().equals(id))) rooms.remove(id);
    }
    public static String source(Picture picture) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((picture.getId() + "\n" + picture.getOriginalUrl() + "\n" + picture.getUrl()).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : bytes) hex.append(String.format("%02x", b & 255));
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
