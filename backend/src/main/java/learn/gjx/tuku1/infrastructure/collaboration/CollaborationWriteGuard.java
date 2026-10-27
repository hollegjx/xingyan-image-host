package learn.gjx.tuku1.infrastructure.collaboration;

import learn.gjx.tuku1.application.collaboration.CollaborationService;
import learn.gjx.tuku1.domain.collaboration.EditRoom;
import learn.gjx.tuku1.exception.*;
import learn.gjx.tuku1.model.entity.Picture;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.*;

/** Bridges the in-memory edit lease with the existing database transaction, without holding a lock during COS IO. */
@Component
public class CollaborationWriteGuard {
    private final CollaborationService service;
    private final CollaborationQueue queue;
    public CollaborationWriteGuard(CollaborationService service, CollaborationQueue queue) { this.service = service; this.queue = queue; }
    public void protect(Picture current, Picture replacement, Long userId, String token, Long revision) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("需要数据库事务");
        final EditRoom room;
        try { room = service.beginWrite(current, userId, token, revision); }
        catch (IllegalStateException e) { throw new BusinessException(ErrorCode.OPERATION_ERROR, e.getMessage()); }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                service.finishWrite(current.getId(), room, status == STATUS_COMMITTED ? replacement : null);
                // tick also refreshes state, so a saturated queue does not drop cleanup or correctness.
                queue.offer(() -> service.changed(current.getId()));
            }
        });
    }
}
