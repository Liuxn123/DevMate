# DevMate 学习项目

项目编号：`P-2026-001`

我们通过逐步实现 DevMate，学习 Java/Spring、HTTP、环境识别、任务编排、审批与 Agent 工具调用。语雀文档描述最终产品目标；代码按小切片推进，每次都能说明输入、程序做了什么、输出如何验证。

## 设计资料

产品定位、功能需求、技术架构、竞品分析、路线和原型见 [`DevMate-语雀/`](DevMate-语雀/)。V1 用户项目先支持 Java/Spring Boot + Maven；Node.js 用户项目延后到 V1.1。DevMate 自己的 Vue 前端使用 Node.js 构建，不等于 V1 对外支持 Node 项目。

项目当前依赖版本和本机环境基线见 [`文档/技术栈与开发环境.md`](文档/技术栈与开发环境.md)，依赖或环境变化后需要同步维护。

## 当前学习切片

浏览器中的 Vue 页面请求 `GET /api/health`，Spring Boot Controller 返回 JSON。后端还提供项目登记 API，并用 SQLite 保存项目记录。接下来逐步加入项目识别、环境扫描、受控任务执行，最后接入 Spring AI。

## 运行方式

后端：在 `devmate-server/` 运行 `mvn spring-boot:run`。

前端：在 `devmate-web/` 运行 `npm install`，随后运行 `npm run dev`。Vite 将 `/api` 请求代理到本地后端。

## 当前路径

`devmate-server/` 后端 · `devmate-web/` 前端 · `DevMate-语雀/` 产品与架构资料 · [`任务清单.md`](任务清单.md) 学习与开发顺序
