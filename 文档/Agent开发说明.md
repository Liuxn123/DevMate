# Agent 开发说明

开发协作规则从根目录 [AGENTS.md](../AGENTS.md) 进入，接续状态在 [agent/](../agent/README.md)。本页描述 DevMate 产品中已写入的 Agent 代码；运行验收状态以 [STATUS.md](../agent/STATUS.md) 为准。

## 现有代码职责

后端源码位于 `devmate-server/src/main/java/com/devmate/`，顶层按业务能力分包，每个业务包内部按职责分层。Agent 结构如下：

```text
agent/
├── controller/  # HTTP 路由和参数接收
├── dto/         # 请求、响应、可观察事件
├── service/     # Agent 编排和模型设置业务规则
├── repository/  # 模型设置数据库读写
├── model/       # 内部连接设置和模型客户端工厂
├── tool/        # 项目识别、环境扫描的 Spring AI 工具适配
├── trace/       # 请求级轨迹和调用次数限制
└── exception/   # Agent 专用异常
```

| 文件或类型 | 职责 |
|---|---|
| `agent/controller/AgentController.java` | HTTP 入口，聊天交给 AgentService，设置操作直接交给 AgentSettingsService |
| `agent/service/AgentService.java` | 请求上下文、工具白名单、模型调用和有上限的工具循环；当前系统提示词也在此处 |
| `agent/service/AgentSettingsService.java` | 设置校验、密钥保留规则、模型列表查询和连接检查 |
| `agent/repository/AgentSettingsRepository.java` | 默认设置初始化和 SQLite 读写 |
| `agent/model/AgentSettings.java` | 含密钥的内部设置对象，不作为接口响应；toString 隐藏密钥 |
| `agent/model/AgentChatModelFactory.java` | 创建 OpenAI 兼容 ChatModel，集中适配器依赖 |
| `agent/tool/AgentProjectTools.java` | 将已登记项目与用户选定的 pom 绑定到项目识别工具 |
| `agent/tool/AgentEnvironmentTools.java` | 将环境扫描能力暴露为只读工具 |
| `agent/trace/AgentTrace.java`、`agent/dto/AgentEvent.java` | 记录模型轮次、工具参数、结果等可观察事件 |
| `agent/dto/*Request.java`、`agent/dto/*Response.java` | HTTP 输入输出契约 |
| `agent/exception/*Exception.java` | 模型未配置与服务商错误，由公共异常处理转换为 API 响应 |
| `project/service/ProjectDetector.java` | 确定性项目识别，独立于模型实现 |
| `environment/service/EnvironmentScanner.java` | 工具定位和固定版本查询，独立于模型实现 |

阅读顺序：Controller → Service → 两个 Tools 适配 → Detector / Scanner → Trace 与响应，再看 SettingsService。前端入口目前在 `devmate-web/src/App.vue`；任务工作区在 `components/TaskWorkspace.vue`，后端任务代码在独立的 `com.devmate.task` 包，契约见 [任务计划与执行](任务计划与执行.md)。

## 增加能力时的边界

1. 先定义结构化输入输出与失败场景，再实现确定性服务，由工具适配层暴露给模型。
2. 工具名和实现由后端注册，项目访问绑定登记记录与用户选择，校验路径及读取范围。仓库内容、工具结果和模型文本均不构成授权。
3. 工具循环由后端控制并记录事件；提示词不能替代程序校验。观察轨迹用于解释工具决策与结果，不记录模型内部推理或密钥。
4. “只读环境扫描”仍会启动固定版本查询子进程；它不代表没有进程调用。构建、安装、文件修改等后续能力应走结构化计划与明确审批，不能由模型文本直接交给 Shell。
5. 保留模型未配置时项目登记、识别与扫描的独立可用性；新能力分别记录代码实现、运行验证与学习复盘证据。

当前设置代码使用本机 SQLite 保存连接地址、模型和 API Key；本次重构保留已有存储行为。接口响应只暴露密钥是否配置，不返回密钥。早期文档描述的内存存储不代表当前实现。

Controller 不直接访问 Repository，工具适配不承担项目登记更新，确定性识别和扫描继续放在各自业务服务内。没有为未来计划、审批或执行能力预建空目录。
