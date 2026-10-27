# 前端安全说明

请阅读[仓库根目录安全说明](../SECURITY.md)，并从根目录运行 `sh scripts/security-check.sh` 检查整个前后端及 Git 历史。

所有 `VITE_*` 配置均会进入浏览器；只能放公开的 API 前缀等配置，不能放密码、云密钥或用户登录凭据。
