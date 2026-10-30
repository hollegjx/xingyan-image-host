package learn.gjx.tuku1.model.vo;

/** 临时凭据仅在本次响应返回；禁止生成包含字段的 toString。 */
public final class UserPasswordResetVO {
    private final String temporaryPassword;
    public UserPasswordResetVO(String temporaryPassword) { this.temporaryPassword = temporaryPassword; }
    public String getTemporaryPassword() { return temporaryPassword; }
}
