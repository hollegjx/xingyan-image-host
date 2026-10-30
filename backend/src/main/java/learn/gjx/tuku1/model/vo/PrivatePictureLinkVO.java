package learn.gjx.tuku1.model.vo;

import lombok.Getter;
import lombok.AllArgsConstructor;
import java.util.Date;

/** 临时凭据仅返回本次响应；不生成包含链接的 toString。 */
@Getter
@AllArgsConstructor
public class PrivatePictureLinkVO {
    private final String url;
    private final Date expiresAt;
}
