package learn.gjx.tuku1.domain.collaboration;

import java.util.UUID;

/** In-memory aggregate. All transitions are atomic; transport and persistence stay outside. */
public final class EditRoom {
    public static final long LEASE_MS = 45_000;
    private final String roomId = UUID.randomUUID().toString();
    private String sourceVersion;
    private long revision;
    private int rotation;
    private int zoom;
    private String ownerConnection, ownerId, ownerName, token;
    private long expiresAt;
    private boolean writing;
    private boolean ownerDisconnected;

    public EditRoom(String sourceVersion) { this.sourceVersion = sourceVersion; }

    public synchronized void enter(String connection, String userId, String name, long now) {
        expire(now);
        require(!writing, "图片正在保存");
        require(ownerConnection == null || ownerConnection.equals(connection), "其他成员正在编辑");
        if (ownerConnection == null) {
            ownerDisconnected = false;
            ownerConnection = connection; ownerId = userId; ownerName = name;
            token = UUID.randomUUID().toString(); revision++;
        }
        expiresAt = now + LEASE_MS;
    }

    public synchronized void action(String connection, long expected, String action, long now) {
        expire(now);
        require(!writing, "图片正在保存");
        require(connection.equals(ownerConnection), "请先取得编辑权");
        require(revision == expected, "编辑状态已更新，请重试");
        switch (action == null ? "" : action) {
            case "ROTATE_LEFT": rotation = (rotation + 3) % 4; break;
            case "ROTATE_RIGHT": rotation = (rotation + 1) % 4; break;
            case "ZOOM_IN": require(zoom < 8, "已达到放大上限"); zoom++; break;
            case "ZOOM_OUT": require(zoom > -8, "已达到缩小下限"); zoom--; break;
            default: throw new IllegalArgumentException("不支持的编辑操作");
        }
        revision++; expiresAt = now + LEASE_MS;
    }

    public synchronized boolean exit(String connection) {
        if (writing && connection.equals(ownerConnection)) { ownerDisconnected = true; return false; }
        if (!writing && connection.equals(ownerConnection)) { release(); return true; }
        return false;
    }

    public synchronized void heartbeat(String connection, long now) {
        expire(now);
        if (connection.equals(ownerConnection)) expiresAt = now + LEASE_MS;
    }

    public synchronized boolean expire(long now) {
        if (!writing && ownerConnection != null && now >= expiresAt) { release(); return true; }
        return false;
    }

    /** Called after locking the DB space and picture rows. Prevent edits during DB commit. */
    public synchronized void beginWrite(String userId, String suppliedToken, Long expected,
                                        String currentSource, long now) {
        expire(now);
        require(!writing, "图片正在保存");
        require(sourceVersion.equals(currentSource), "原图已变化，请重新打开编辑器");
        if (ownerConnection != null || suppliedToken != null) {
            require(ownerConnection != null && ownerId.equals(userId) && token.equals(suppliedToken),
                    "协同编辑权已失效，或其他成员正在编辑");
            require(expected != null && revision == expected, "编辑状态已更新，不能保存旧结果");
        }
        writing = true;
    }

    public synchronized void finishWrite(String committedSource) {
        writing = false;
        if (committedSource != null) { sourceVersion = committedSource; rotation = 0; zoom = 0; release(); }
        else if (ownerDisconnected) release();
    }

    public synchronized boolean refreshSource(String currentSource) {
        if (writing || sourceVersion.equals(currentSource)) return false;
        sourceVersion = currentSource; rotation = 0; zoom = 0; release(); return true;
    }

    public synchronized Snapshot snapshot(String connection) {
        return new Snapshot(roomId, sourceVersion, revision, rotation, zoom, ownerId, ownerName,
                connection.equals(ownerConnection), connection.equals(ownerConnection) ? token : null, writing);
    }

    public synchronized boolean isWriting() { return writing; }
    private void release() {
        ownerConnection = null; ownerId = null; ownerName = null; token = null; expiresAt = 0; revision++;
        ownerDisconnected = false;
    }
    private static void require(boolean ok, String message) { if (!ok) throw new IllegalStateException(message); }

    public static final class Snapshot {
        public final String roomId, sourceVersion, revision, editorId, editorName, leaseToken;
        public final int rotation, zoom;
        public final boolean editing, saving;
        Snapshot(String roomId, String source, long revision, int rotation, int zoom, String owner,
                 String name, boolean editing, String token, boolean saving) {
            this.roomId = roomId; this.sourceVersion = source; this.revision = Long.toString(revision);
            this.rotation = rotation; this.zoom = zoom; this.editorId = owner; this.editorName = name;
            this.editing = editing; this.leaseToken = token; this.saving = saving;
        }
    }
}
