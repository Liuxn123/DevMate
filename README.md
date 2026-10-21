# DevMate 学习项目

项目编号：`P-2026-001`

我们通过逐步实现 DevMate，学习 Java/Spring、HTTP、环境识别、任务编排、审批与 Agent 工具调用。语雀文档描述最终产品目标；代码按小切片推进，每次都能说明输入、程序做了什么、输出如何验证。

## 设计资料

产品定位、功能需求、技术架构、竞品分析、路线和原型见 [`文档/`](文档/)。V1 用户项目先支持 Java/Spring Boot + Maven；Node.js 用户项目延后到 V1.1。DevMate 自己的 Vue 前端使用 Node.js 构建，不等于 V1 对外支持 Node 项目。

项目当前依赖版本和本机环境基线见 [`文档/技术栈与开发环境.md`](文档/技术栈与开发环境.md)，依赖或环境变化后需要同步维护。

## 当前学习切片

浏览器中的 Vue 页面请求 `GET /api/health`，Spring Boot Controller 返回 JSON。后端提供 SQLite 项目登记、M2 只读项目识别和本机 JDK/Maven/Git 扫描入口。M2 主流程已通过页面验收；M3 Spring AI 只读工具循环及模型设置首版已写，待用户编译和运行验收。后续逐步加入结构化计划、审批与受控执行。

## 运行方式

后端：在 `devmate-server/` 运行 `mvn spring-boot:run`。

前端：在 `devmate-web/` 运行 `npm install`，随后运行 `npm run dev`。Vite 将 `/api` 请求代理到本地后端。

## 当前路径

`devmate-server/` 后端 · `devmate-web/` 前端 · `文档/` 产品与架构资料 · [`任务清单.md`](任务清单.md) 学习与开发顺序


## 目录与协作入口

完整目录职责见 [目录结构](文档/目录结构.md)。开发 Agent 先读 [AGENTS.md](AGENTS.md)，再按 [接续入口](agent/README.md) 阅读当前状态和待办；产品 Agent 的代码职责见 [Agent 开发说明](文档/Agent开发说明.md)。

根目录的 [项目主页](项目主页.md) 用于 Obsidian 导航，[任务清单](任务清单.md) 维护学习与开发待办。运行数据库、依赖、构建产物和 IDE 文件由 Git 忽略；数据库保留当前位置。上面的安装、构建和启动命令供参考，执行前仍需明确审批。
