package learn.gjx.tuku1.model.vo;

/** token 仅创建响应返回，不生成带字段的 toString。 */
public final class TeamInvitationCreatedVO {
    private final Long id;
    private final String token;
    public TeamInvitationCreatedVO(Long id, String token) { this.id = id; this.token = token; }
    public Long getId() { return id; }
    public String getToken() { return token; }
}
