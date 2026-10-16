---
title: DevMate 当前状态
created: 2026-10-03
updated: 2026-10-16
status: active
type: project-status
project_id: P-2026-001
tags: [project, devmate, learning]
---

# DevMate 当前状态

## 当前阶段

M1 的本地项目登记 API、Spring JDBC Repository、SQLite 数据源、Flyway 初始迁移和统一参数错误响应已实现并通过运行验收。POST 成功返回 201；重启后 GET 仍读到同一条记录；空参数返回 400；重复路径返回 409。当前登记只保存名称和规范化路径，不检查路径是否存在，不扫描目录或运行用户项目。项目管理页面首个前端切片已接入该 API；M1 学习复盘待完成。

M0 的前后端运行链路已搭建并启动：Vue 页面请求 Spring Boot `GET /api/health`，后端返回状态、应用名和 Java 运行版本。通过 HTTP 已确认前端页面返回 200，后端直连与 Vite 代理均返回 `UP`。浏览器 Network 面板观察和学习者解释仍待完成。

## 环境观察

2026-10-13 观察：`java -version` 显示 JDK 11.0.28；`mvn -version` 显示 Maven 3.9.4 使用 `E:\learn\java\jdk` 下的 JDK 21.0.3；Node.js 24.19.0、npm 11.17.0。Spring Boot 日志和 `/api/health` 响应均确认服务进程使用 Java 21.0.3。Maven 使用临时仓库启动，避免写入工作区外的只读默认仓库；本次没有修改系统级环境变量。

2026-10-16 M1 验收：Maven 使用临时仓库完成 13 个 Java 源文件编译；Flyway 在 SQLite 3.49 上验证并应用 V1。8080 已被 PID 12456 的 Java 进程占用，未停止该进程；DevMate M1 实例改用 `127.0.0.1:18080`。登记记录 ID `8040b8a4-f236-49dc-8245-7be773790736` 在重启后仍可读取。当前 M1 后端进程保留运行于 18080。

## 下一步

在浏览器查看项目管理页面：读取现有登记记录，再登记一个本地目录并确认列表更新。随后复盘 Controller → Service → Repository → SQLite 调用链，以及 Flyway 如何应用 V1、400/409 错误为何由服务端返回。M0 Network 面板观察仍可补做。
