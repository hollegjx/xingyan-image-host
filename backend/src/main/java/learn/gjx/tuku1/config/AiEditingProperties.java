package learn.gjx.tuku1.config;

import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.util.*;

/** Fail closed until credentials, a spending budget and real acceptance have been configured. */
@Data
@Component
@ConfigurationProperties(prefix = "picture.ai-editing")
public class AiEditingProperties {
    private boolean enabled;
    private boolean privateEnabled;
    @ToString.Exclude private String apiKey = "";
    private String workspaceId = "";
    private Set<Long> allowedUserIds = new HashSet<>();
    private int dailyUserLimit = 10;
    private int dailyGlobalLimit = 50;
    private Set<String> resultHosts = new HashSet<>(Arrays.asList(
            // Exact accelerated hosts in the official qwen-image-edit-api FAQ (2026-10-24).
            // a717 also verified against a real authenticated generation receipt.
            // Inventory can change: keep fail-closed; never allow all *.aliyuncs.com.
            "dashscope-a717.oss-accelerate.aliyuncs.com",
            "dashscope-66f3.oss-accelerate.aliyuncs.com",
            "dashscope-7c2c.oss-accelerate.aliyuncs.com",
            "dashscope-2522.oss-accelerate.aliyuncs.com",
            "dashscope-c72b.oss-accelerate.aliyuncs.com",
            "dashscope-0484.oss-accelerate.aliyuncs.com",
            "dashscope-7e0f.oss-accelerate.aliyuncs.com",
            "dashscope-5859.oss-accelerate.aliyuncs.com",
            "dashscope-5496.oss-accelerate.aliyuncs.com",
            "dashscope-35f9.oss-accelerate.aliyuncs.com",
            "dashscope-31d9.oss-accelerate.aliyuncs.com",
            "dashscope-7f1f.oss-accelerate.aliyuncs.com",
            "dashscope-cc75.oss-accelerate.aliyuncs.com",
            "dashscope-64e9.oss-accelerate.aliyuncs.com",
            "vigen-invi.oss-cn-shanghai.aliyuncs.com",
            "dashscope-result-bj.oss-cn-beijing.aliyuncs.com",
            "dashscope-result-sz.oss-cn-shenzhen.aliyuncs.com",
            "dashscope-result-hz.oss-cn-hangzhou.aliyuncs.com",
            "dashscope-result-wlcb.oss-cn-wulanchabu.aliyuncs.com"));

    public boolean configured() {
        return apiKey != null && !apiKey.trim().isEmpty() && workspaceId != null
                && workspaceId.matches("ws-[a-z0-9]{8,64}");
    }
}
