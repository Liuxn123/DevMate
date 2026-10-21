# devmate-server：后端学习切片

当前后端已实现 HTTP 健康检查、本地项目登记的 SQLite 持久化、M2 只读项目识别/本机工具扫描，以及 M3 Agent 只读工具调用的首版代码。M3 尚未编译或运行验收。

## 运行

```powershell
mvn spring-boot:run
```

后端只监听 `127.0.0.1:8080`。接口：`GET http://127.0.0.1:8080/api/health`。

## 代码阅读顺序

源码顶层按业务能力分为 `agent`、`project`、`environment`、`health` 和 `common`，各包内部再划分 `controller`、`dto`、`service`、`repository`、`model`、`exception` 等实际职责。Agent 额外包含 `tool` 和 `trace`；设置数据库读写与模型客户端构造已独立提取。完整路径及阅读说明见 [Agent 开发说明](../文档/Agent开发说明.md)。

1. `DevMateApplication`：Spring Boot 从这里启动并扫描组件。
2. `HealthController` / `ProjectController`：`@RestController` 注册 HTTP 入口，映射 URL 到 Java 方法。
3. `ProjectService`：Spring 把 `ProjectRepository` 注入进来，处理路径规范化、重复登记等业务规则。
4. `ProjectRepository`：通过 `JdbcTemplate` 执行带参数的 SQL，读写 SQLite。
5. `db/migration/V1__create_project_table.sql`：Flyway 启动时应用版本化建表脚本。
6. `ApiExceptionHandler`：把参数错误、重复登记和找不到项目转换为稳定的 HTTP 错误响应。
7. `ProjectDetector`：限制 XML 外部实体和读取大小，识别 Maven / Spring Boot 特征并返回证据。
8. `EnvironmentScanner`：定位本机 JDK、Maven、Git 并调用固定版本参数；子进程在临时目录运行并设置超时。
9. `application.yml`：应用名、数据库文件、端口和本地监听地址等配置。

先运行后端并用浏览器或 `Invoke-RestMethod` 请求接口，再启动 `devmate-web` 看 Vite 如何代理 `/api`。

## 项目登记 API（M1）

- `POST /api/v1/projects`：登记一个本地目录。请求 JSON：`{"name":"demo","rootPath":"D:/workspace/demo"}`。只保存名称和规范化后的路径，不扫描目录、不运行项目。
- `GET /api/v1/projects`：按创建时间倒序读取登记列表。
- `GET /api/v1/projects/{id}`：按 ID 读取登记详情。

SQLite 默认文件为当前工作目录下的 `devmate.db`；可通过 `DEVMATE_DB_PATH` 指定数据库文件。Flyway 启动时执行 `src/main/resources/db/migration` 中尚未应用的版本化 SQL。

示例：

```powershell
Invoke-RestMethod -Method Post -Uri http://127.0.0.1:8080/api/v1/projects -ContentType 'application/json' -Body '{"name":"demo","rootPath":"D:/workspace/demo"}'
Invoke-RestMethod -Uri http://127.0.0.1:8080/api/v1/projects
```

阅读顺序：`ProjectController`（HTTP/API）→ `ProjectService`（业务规则、路径规范化）→ `ProjectRepository`（JdbcTemplate SQL）→ Flyway 迁移 SQL（表结构）。Spring 通过构造器注入把这些对象连接起来。

本机 `java -version` 和 `mvn -version` 当前指向不同 JDK，后端启动时应观察响应中的 Java 版本。

## M2 只读识别与环境扫描

- `POST /api/v1/projects/{id}/inspect`：识别已登记项目根目录的 Maven / Spring Boot 线索，返回 `projectType`、Java 版本声明、Maven Wrapper 状态、证据和提示；不会递归扫描或运行项目脚本。
- `GET /api/v1/environment/snapshot`、`POST /api/v1/environment/scan`：查询 JDK、Maven、Git 的版本和可执行文件路径。扫描使用固定版本参数，不修改 PATH/JAVA_HOME 或配置文件。
- 环境扫描只在用户手动触发时执行；页面运行验收已通过，异常/超时场景仍待验。

## M3 Agent 对话（首版，待运行验收）

- `GET /api/v1/agent/status`：返回模型是否配置，不返回密钥。
- `GET /api/v1/agent/settings`、`PUT /api/v1/agent/settings`：读取/保存偏好设置中的 DeepSeek 服务商、模型与 API Key 状态；密钥不回显。
- `POST /api/v1/agent/settings/test`：对当前已保存设置发起一次简短模型请求，验证连接。
- `POST /api/v1/agent/messages`：请求示例：`{"message":"检查我的项目和本机环境","projectId":"<已登记项目 ID>","pomPath":"devmate-server/pom.xml"}`。项目识别工具只有在传入有效登记项目与候选 pom 路径时才注册；环境扫描工具只查询 JDK、Maven、Git 版本。
- 响应包含最终回答、工具轮数和模型请求/工具调用/工具结果事件。工具循环由后端手动推进，最多 3 轮；没有任意命令执行工具。
- 模型默认使用 DeepSeek Flash，通过 DeepSeek 的 OpenAI 兼容接口调用。设置页提供模型选择与连接测试。API Key 只保存在后端进程内存中，不写数据库、浏览器存储或仓库；后端重启后需重新输入。对话消息和工具事实会发送到 DeepSeek 处理。
