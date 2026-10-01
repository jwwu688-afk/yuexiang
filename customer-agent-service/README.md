# 悦享商城客服 Agent

这是悦享商城的独立 Python Agent 服务。Java 单体保管账号、商品、订单、规则、会话和工单；Python 只通过带签名的有限权限令牌调用 Java 的只读内部接口。客服模型不能直接访问商城数据库，也不能创建人工工单。

## PyCharm 启动

1. 使用 Python 3.12 创建虚拟环境。
2. 运行 `python -m pip install -e ".[test]"` 安装依赖。
3. 将 `.env.example` 复制为被 Git 忽略的 `.env.local`，填写模型配置，以及与 Java 一致的 `HM_AGENT_TOKEN_SECRET` 和 `HM_AGENT_SERVICE_SECRET`。
4. 确认 Java 单体已在 `127.0.0.1:8080` 启动，RAG 问答服务已在 `127.0.0.1:8011` 启动。
5. 运行 `uvicorn app.main:app --host 127.0.0.1 --port 8001`。

`GET /health` 应返回 `{"status":"ok"}`。浏览器只访问 Java 的 `/customer-service/**` 接口，Java 再把受控任务派发到本服务。

## 行为边界

- Agent 可查询在售商品、本人订单、已有物流信息和已发布政策。
- RAG 只查询说明书和产品知识；商城实时商品、订单、物流和政策仍由 Java 工具提供。
- Agent 将 RAG 知识与 Java 工具结果合并为一条最终回复；RAG 返回的合规图片会随回复展示。
- `HM_RAG_BASE_URL` 默认为 `http://127.0.0.1:8011`；`HM_RAG_TIMEOUT_SECONDS` 控制请求超时；`HM_RAG_IMAGE_ALLOWED_ORIGINS` 是逗号分隔的图片源白名单。
- Agent 不执行支付、取消、退款或地址修改。
- 用户要求人工时，Agent 只提示点击页面上的“转人工”按钮；浏览器确认后直接请求 Java 创建工单。
- 工具没有返回可核实事实时，不转发模型自由文本，也不编造答案。
- SQLite 检查点写入 `HM_AGENT_CHECKPOINT_DB`，默认是 `data/customer-agent-checkpoints.sqlite3`。

## 测试

```powershell
python -m pytest tests -q
```

Dockerfile 暴露 8001 端口。部署时只允许 Java 服务访问 Python 入口，并把 `.env.local` 中的配置改为环境变量注入。
