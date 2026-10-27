package learn.gjx.tuku1.application.collaboration;

import learn.gjx.tuku1.model.entity.Picture;

/** Port: authentication/current permission lookup, independent of the socket transport. */
public interface CollaborationAccess {
    Actor authorize(String sessionId, Long userId, Long pictureId);
    final class Actor {
        public final Long id;
        public final String name;
        public final Picture picture;
        public Actor(Long id, String name, Picture picture) { this.id = id; this.name = name; this.picture = picture; }
    }
}
