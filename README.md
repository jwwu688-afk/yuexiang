# 悦享商城

一个 Java 商城应用与一个独立 Python 客服 Agent。Java 源码以原 `hm-service`、`hm-common` 为基础，保留 `com.hmall` 包名和现有 `hmall` 数据库名。

## 本地启动

使用 Java 11 运行目标（Java 17 运行时按旧版 MyBatis-Plus 要求配置 `--add-opens java.base/java.lang.invoke=ALL-UNNAMED`），在 IDEA 打开本目录并运行 `com.hmall.HMallApplication`。MySQL 需已有 `hmall` 业务表，设置 `HM_DB_PASSWORD`、`HM_JWT_PASSWORD`，并在 `hm-service/src/main/resources` 放置未提交的 `hmall.jks`。Java 默认监听 8080。

在 PyCharm 打开 `customer-agent-service`，用 Python 3.12 和 `.env.local` 启动 `uvicorn app.main:app --host 127.0.0.1 --port 8001`。Nginx 从 `frontend` 部署页面，监听 18080，将 API 代理到 Java 8080。

迁移和验收步骤见 `docs/`；未提供真实商城政策和模型密钥时，规则咨询与模型联调需要单独配置。
