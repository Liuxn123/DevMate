# devmate-server：后端学习切片

当前已实现两个后端学习切片：HTTP 健康检查，以及本地项目登记的 SQLite 持久化。

## 运行

```powershell
mvn spring-boot:run
```

后端只监听 `127.0.0.1:8080`。接口：`GET http://127.0.0.1:8080/api/health`。

## 代码阅读顺序

1. `DevMateApplication`：Spring Boot 从这里启动并扫描组件。
2. `HealthController` / `ProjectController`：`@RestController` 注册 HTTP 入口，映射 URL 到 Java 方法。
3. `ProjectService`：Spring 把 `ProjectRepository` 注入进来，处理路径规范化、重复登记等业务规则。
4. `ProjectRepository`：通过 `JdbcTemplate` 执行带参数的 SQL，读写 SQLite。
5. `db/migration/V1__create_project_table.sql`：Flyway 启动时应用版本化建表脚本。
6. `ApiExceptionHandler`：把参数错误、重复登记和找不到项目转换为稳定的 HTTP 错误响应。
7. `application.yml`：应用名、数据库文件、端口和本地监听地址等配置。

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
