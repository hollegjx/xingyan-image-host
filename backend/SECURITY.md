# 后端安全说明

请阅读[仓库根目录安全说明](../SECURITY.md)，并从根目录运行 `sh scripts/security-check.sh` 检查整个前后端及 Git 历史。

运行配置使用环境变量和安全的示例文件。真实 `application.yml`、云访问凭据、数据库密码与业务数据不应进入提交；发布前不要合入旧开发仓库的敏感历史。
