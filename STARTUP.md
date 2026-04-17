# TinyRAG 启动与运行指南

本文说明如何从空环境到成功打开 Web 界面、完成上传与流式问答。主应用为 **`tinyrag-bootstrap`** 模块；根目录另有可选的 **`tinyrag-mcp-server`**（MCP 服务，默认不随主应用启动）。

---

## 1. 环境要求

| 组件 | 版本 / 说明 |
|------|-------------|
| **JDK** | **17 或以上**（与 `pom.xml` 中 `maven.compiler.release` 一致） |
| **Maven** | 3.8+；也可使用仓库根目录的 **`mvnw` / `mvnw.cmd`**，无需本机安装 Maven |
| **MySQL** | 5.7+ / 8.x；需提前建库，供 JPA 持久化会话与消息 |
| **Milvus** | **可选**。完整 RAG 需 Milvus（gRPC，默认端口 **19530**）；无 Milvus 时可使用 **`simple-vector` 内存向量库**（进程重启后向量丢失） |
| **网络** | 需能访问大模型与向量、重排接口（见下文「模型与外部服务」） |

---

## 2. 获取代码与编译

在仓库 **`tinyrag`** 目录（含 `pom.xml` 与 `mvnw` 的目录）执行：

```bash
# Linux / macOS
./mvnw -pl tinyrag-bootstrap -am -DskipTests package

# Windows（PowerShell / CMD）
mvnw.cmd -pl tinyrag-bootstrap -am -DskipTests package
```

说明：

- **`-pl tinyrag-bootstrap`**：只构建主应用模块。
- **`-am`**：同时构建其依赖模块（若将来有共享模块会一并编译）。

开发阶段也可只编译：

```bash
./mvnw -pl tinyrag-bootstrap -am -DskipTests compile
```

---

## 3. 准备 MySQL 数据库

1. 启动 MySQL 服务。
2. 创建数据库（名称需与 JDBC URL 一致，默认 **`tinyrag`**）：

```sql
CREATE DATABASE tinyrag DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

3. 创建拥有该库权限的用户（或使用 `root`），并记下账号密码。

首次启动时，JPA 使用 **`ddl-auto: update`**，会自动创建/更新 `chat_session`、`chat_message` 等表，**无需手工导入 SQL**。

---

## 4. 配置方式说明

配置优先级：**环境变量 / 启动参数** 覆盖 **`application.yaml` 默认值**。

主配置文件路径：

`tinyrag-bootstrap/src/main/resources/application.yaml`

### 4.1 数据库（必填）

| 环境变量 | 含义 | 默认（示例） |
|----------|------|----------------|
| `MYSQL_URL` | JDBC URL | `jdbc:mysql://127.0.0.1:3306/tinyrag?...` |
| `MYSQL_USERNAME` | 用户名 | `root` |
| `MYSQL_PASSWORD` | 密码 | 见 yaml，**生产环境务必修改** |

### 4.2 大模型与 Embedding（必填）

当前默认对接 **阿里云 DashScope 兼容模式**（可在 yaml 中改为 SiliconFlow 等兼容 OpenAI 的地址）。

| 环境变量 | 含义 |
|----------|------|
| `SILICONFLOW_BASE_URL` | 兼容 API 根地址（变量名沿用历史，实际可为 DashScope） |
| `SILICONFLOW_API_KEY` | API Key |

yaml 中还配置了聊天模型、Embedding 模型名称；修改对应 `spring.ai.openai.*` 即可。

### 4.3 重排序（Rerank）

RAG 流程会调用 `app.rag.rerank-endpoint`（默认 SiliconFlow 的 rerank 地址）。若 Key 或网络不可用，实现上通常会回退到纯向量分数排序，但建议配置有效 Key 以保证效果。

### 4.4 Milvus（可选，默认启用）

| 环境变量 | 含义 | 默认 |
|----------|------|------|
| `MILVUS_HOST` | gRPC 地址 | 示例 IP（请改为你的 Milvus 主机） |
| `MILVUS_PORT` | gRPC 端口 | `19530` |
| `MILVUS_USERNAME` / `MILVUS_PASSWORD` | 认证 | 按部署情况填写 |
| `MILVUS_COLLECTION` | 集合名 | `tinyrag_kb` |
| `EMBEDDING_DIMENSION` | 向量维度 | 需与 Embedding 模型一致（默认 `1024`） |

**注意：** 浏览器能打开的 Milvus 管理页端口，往往 **不是** gRPC 的 `19530`，请以实际 Milvus 文档为准。

**连通性自检（Windows PowerShell）：**

```powershell
Test-NetConnection <MILVUS_HOST> -Port 19530
```

若超时或失败，请检查虚拟机/防火墙/端口映射；或改用下一节的 **`simple-vector`** 模式先跑通业务。

---

## 5. 两种运行模式

### 模式 A：Milvus（生产 / 完整能力）

依赖：MySQL + 可连通的 Milvus + 模型 API。

### 模式 B：`simple-vector`（本地试用、无 Milvus）

激活 Spring Profile：**`simple-vector`**。

效果：

- 排除 Milvus 自动配置，使用 **内存向量库**（`SimpleVectorStore`）。
- **进程退出后向量清空**；适合演示与开发，不适合多实例共享检索。

对应文件：

- `application-simple-vector.yaml`（排除 Milvus 自动配置）
- `SimpleVectorStoreConfiguration.java`（`@Profile("simple-vector")`）

---

## 6. 启动主应用

在 **`tinyrag` 根目录**执行（推荐）：

### 6.1 使用 Milvus

**Linux / macOS：**

```bash
export MYSQL_PASSWORD='你的MySQL密码'
export SILICONFLOW_API_KEY='你的API_Key'
export MILVUS_HOST='127.0.0.1'   # 或你的 Milvus 主机

./mvnw -pl tinyrag-bootstrap spring-boot:run
```

**Windows（PowerShell）：**

```powershell
$env:MYSQL_PASSWORD="你的MySQL密码"
$env:SILICONFLOW_API_KEY="你的API_Key"
$env:MILVUS_HOST="127.0.0.1"

.\mvnw.cmd -pl tinyrag-bootstrap spring-boot:run
```

### 6.2 不使用 Milvus（simple-vector）

**Linux / macOS：**

```bash
export MYSQL_PASSWORD='你的MySQL密码'
export SILICONFLOW_API_KEY='你的API_Key'
export SPRING_PROFILES_ACTIVE=simple-vector

./mvnw -pl tinyrag-bootstrap spring-boot:run
```

**Windows（PowerShell）：**

```powershell
$env:MYSQL_PASSWORD="你的MySQL密码"
$env:SILICONFLOW_API_KEY="你的API_Key"
$env:SPRING_PROFILES_ACTIVE="simple-vector"

.\mvnw.cmd -pl tinyrag-bootstrap spring-boot:run
```

等价启动参数写法：

```bash
./mvnw -pl tinyrag-bootstrap spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=simple-vector"
```

### 6.3 使用已打包的 JAR

先执行第 2 节 `package`，然后：

```bash
java -jar tinyrag-bootstrap/target/tinyrag-bootstrap-0.0.1-SNAPSHOT.jar
```

同样可通过环境变量或参数传入 Profile、数据库与 Key。

### 6.4 IDE 中运行

主类：**`com.fja.ai.tinyrag.TinyragApplication`**

模块：**`tinyrag-bootstrap`**

在 Run Configuration 中设置：

- **Environment variables**：`MYSQL_PASSWORD`、`SILICONFLOW_API_KEY`、`SPRING_PROFILES_ACTIVE`（如需）等  
- 或 **Program arguments**：`--spring.profiles.active=simple-vector`

---

## 7. 启动后验证

| 项目 | 说明 |
|------|------|
| **Web 界面** | 浏览器打开 **http://localhost:8080/** （端口以 `server.port` 为准，默认 `8080`） |
| **静态页** | 内置 `static/index.html`，无需单独启动前端 |
| **健康检查** | 若未关闭 actuator，可按你项目中的实际端点访问；默认可先以首页是否打开为准 |

### 7.1 主要 HTTP 能力（便于联调）

- **RAG 流式问答**：`POST /api/rag/chat/stream`（`Accept: text/event-stream`）
- **文档上传入库**：`POST /api/rag/knowledge/upload`（`multipart/form-data`，字段 `file`、`kb`）
- **会话**：`GET/POST /api/chat/sessions`，`GET /api/chat/sessions/{id}/messages`，`POST /api/chat/sessions/{id}/turns` 等

---

## 8. 可选：MCP 子模块 `tinyrag-mcp-server`

与主应用 **独立进程**，默认端口 **8081**（见 `tinyrag-mcp-server/src/main/resources/application.yaml`）。

```bash
./mvnw -pl tinyrag-mcp-server spring-boot:run
```

仅在需要 MCP 客户端对接时启动；主问答界面不依赖该模块。

---

## 9. 常见问题

### 9.1 启动失败：数据库连接错误

- 确认 MySQL 已启动，库名、用户、密码与 `MYSQL_*` 一致。
- JDBC URL 中的时区、编码参数与 MySQL 版本匹配。

### 9.2 Milvus：`DEADLINE_EXCEEDED` / 连接超时

- 确认连接的是 **gRPC 端口**（常为 `19530`），且 Milvus 监听 `0.0.0.0` 或可达地址。
- 虚拟机场景：桥接/NAT、防火墙、端口转发。
- 临时绕过：使用 **`SPRING_PROFILES_ACTIVE=simple-vector`**。

### 9.3 编译报错：类文件版本过高 / 无法加载 Spring Boot

- 使用 **JDK 17+** 编译与运行；`JAVA_HOME` 指向正确版本。

### 9.4 前端 Markdown / CDN 资源加载失败

- 首页依赖 jsDelivr 加载 marked、DOMPurify；**纯内网**环境需改为本地静态资源或允许该域名访问。

### 9.5 历史会话加载失败（已修复类问题）

- 若使用旧构建，确保 Controller 中 **`@PathVariable("id")`** 显式命名，且 Maven 开启 **`-parameters`**（父 `pom.xml` 已配置 `parameters`）。

---

## 10. 安全与合规建议

- **切勿**将真实 `SILICONFLOW_API_KEY` / `MYSQL_PASSWORD` 提交到 Git；使用环境变量或本地 `application-local.yaml`（并已加入 `.gitignore`）。
- 仓库内 yaml 中的示例 Key 仅作占位，**上线前必须替换**。
- `simple-vector` 仅内存存储，**不要**用于需要持久化检索结果的生产环境。

---

## 11. 文档与代码索引

| 内容 | 路径 |
|------|------|
| 主配置 | `tinyrag-bootstrap/src/main/resources/application.yaml` |
| 无 Milvus Profile | `tinyrag-bootstrap/src/main/resources/application-simple-vector.yaml` |
| 内存向量配置 | `.../config/SimpleVectorStoreConfiguration.java` |
| 内置前端 | `tinyrag-bootstrap/src/main/resources/static/index.html` |
| 项目总览与接口摘要 | 仓库根目录 `README.md` |

---

若你后续增加 Docker Compose（MySQL + Milvus + 应用），可在此文档末尾追加一节「容器化启动」，与本文环境变量保持一致即可。
