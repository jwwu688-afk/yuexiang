# 悦享商城

悦享商城由一个 Java 单体应用和一个独立 Python 客服 Agent 组成。Java 项目保留原有 `com.hmall` 包名和 `hmall` 数据库名，商品、购物车、用户、交易、支付、客服会话、政策和工单都由 `hm-service` 在 8080 端口提供。

```text
浏览器 :18080 → Nginx → Java 单体 :8080 → Python Agent :8001
                               ↘ MySQL / Redis
```

## 1. IDEA 启动 Java 单体

1. 在 IDEA 打开本项目根目录，使用 JDK 11 或 17，Maven 导入根 `pom.xml`。
2. 确认 `hm-service/src/main/resources/hmall.jks` 存在。
3. 为 `com.hmall.HMallApplication` 设置以下环境变量：

```text
HM_DB_HOST=localhost
HM_DB_PASSWORD=你的 MySQL root 密码
HM_JWT_PASSWORD=hmall123
HM_AGENT_TOKEN_SECRET=至少32个字符且与Python一致
HM_AGENT_SERVICE_SECRET=与Python一致的服务密钥
HM_AGENT_URL=http://127.0.0.1:8001
```

4. 运行 `HMallApplication`。Java 默认监听 `8080`，Flyway 会在现有 `hmall` 库中创建六张 `customer_*` 客服表。

使用 JDK 17 遇到旧版 MyBatis-Plus 反射限制时，在 VM options 加入：

```text
--add-opens java.base/java.lang.invoke=ALL-UNNAMED
```

## 2. PyCharm 启动 Python Agent

1. 在 PyCharm 打开 `customer-agent-service`，选择 Python 3.12。
2. 安装依赖：`python -m pip install -e ".[test]"`。
3. 将 `.env.example` 复制为 `.env.local`，填写模型配置和与 Java 相同的两个内部密钥。
4. 运行：`uvicorn app.main:app --host 127.0.0.1 --port 8001`。
5. 访问 `http://127.0.0.1:8001/health`，应返回 `{"status":"ok"}`。

## 3. 启动页面

`frontend/conf/nginx.conf` 已把全部商城 API 和客服 SSE 请求代理到 Java `8080`。将 `frontend/conf/nginx.conf`、`frontend/html` 同步到现有 `D:\code\hmall-nginx` 后：

```powershell
cd D:\code\hmall-nginx
.\nginx.exe -t
.\nginx.exe -s reload
```

浏览器访问 `http://localhost:18080`。

## 验证命令

```powershell
mvn -o -pl hm-service -am test
python -m pytest customer-agent-service/tests -q
node --test frontend/tests/customer-service-contract.test.js
```

商城政策表没有默认演示数据；需要先录入已审核并处于 `PUBLISHED` 状态的政策，Agent 才会引用规则答复。
