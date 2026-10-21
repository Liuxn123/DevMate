# P-2026-001 DevMate 学习项目

- 项目编号：P-2026-001
- 项目目录：`E:\obsidian\projects\P-2026-001-DevMate`
- Vault 入口：`E:\obsidian\myself\03-项目\P-2026-001-DevMate`

## 项目目标

这是一个通过实现 DevMate 来学习 Agent 与本地开发环境管理的项目。语雀文档描述目标产品；每次实现优先做小型、可观察的纵向切片，先理解请求、程序决策和工具结果，再逐步增加持久化、环境检测和 Agent 能力。

## 现行规划

产品、需求、架构、路线、竞品分析和原型以 `文档/` 为准。当前 V1 用户项目范围是 Java/Spring Boot + Maven；Node.js 用户项目支持放在 V1.1。DevMate 自身的 Vue 前端仍使用 Node.js 构建。

变更系统环境、安装工具、运行项目脚本或修改用户项目配置都需要明确审批。模型输出不能直接执行为命令。

## Agent 接续与文档职责

- 本文件是开发 Agent 的仓库规则入口；接续顺序为 `agent/README.md` → `agent/STATUS.md` → `任务清单.md` → `agent/HANDOFF.md`，再按需阅读里程碑和产品资料。
- `agent/` 存放开发协作上下文；产品运行时 Agent 代码位于 `devmate-server/src/main/java/com/devmate/agent/`，两者职责分开。
- `任务清单.md` 是学习与开发待办真源；`agent/TASKS.md` 只提供导航。`TASKS-AI.md`、`TASKS-MANUAL.md` 是项目工作台的任务同步接口，保留文件名、表头、ID 和同步标记，不复制出新的待办真源。
- `STATUS.md` 只维护当前状态、验证证据和下一步；`MILESTONES.md` 维护阶段验收标准；`HANDOFF.md` 记录近期变更，旧记录按月归档到 `agent/history/`。
- 接续时不默认读取全部历史；排查历史问题时再查归档。历史记录中的旧路径代表当时状态，不作为现行目录依据。
- 区分“代码已实现”“运行已验证”“学习已复盘”，没有证据不标记完成。发生实质变化后更新对应状态和交接，纯问答不记流水账。

## 目录与实现约定

- 目录职责及固定入口见 `文档/目录结构.md`；产品 Agent 的代码阅读和扩展边界见 `文档/Agent开发说明.md`。
- 后端顶层按业务能力分包，内部按实际职责划分 controller、dto、service、repository、model、exception；Agent 额外使用 tool 和 trace。前端保留现有 Vue/Vite 结构；当前阶段不为未来能力预建空模块。
- 保留已有未提交修改。数据库、构建输出、依赖和密钥不提交仓库；数据库迁移 SQL 应纳入版本管理。
- 不主动启动服务、执行构建或调用真实模型；需要运行验证时说明具体命令、影响与尚未验证项，取得明确审批后执行。
