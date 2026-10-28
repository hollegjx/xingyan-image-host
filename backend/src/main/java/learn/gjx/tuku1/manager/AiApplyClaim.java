package learn.gjx.tuku1.manager;

import lombok.Value;

/** Server-only claim; never accepted in an HTTP upload DTO. */
@Value
public class AiApplyClaim {
    String taskId;
    String token;
    Long userId;
    Long pictureId;
    String sourceUrl;
}
