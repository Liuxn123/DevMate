package com.devmate.task.service;

import com.devmate.project.dto.ProjectEnvironment;
import com.devmate.project.model.ProjectRecord;
import com.devmate.task.dto.PlanRequest;
import com.devmate.task.model.ExecutionPlan;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;

/** Model output never supplies paths, arguments or executable names. */
@Component
public class PlanPolicy {
    public static final Set<String> ACTIONS = Set.of("MAVEN_PACKAGE", "MAVEN_VERIFY", "JAVA_JAR");

    public ExecutionPlan create(ProjectRecord project, ProjectEnvironment settings, PlanRequest request, String action) {
        if (!ACTIONS.contains(action)) throw new IllegalArgumentException("不支持的动作类型");
        if (settings.javaHome().isBlank() || settings.mavenHome().isBlank()) {
            throw new IllegalArgumentException("请先保存该项目的 JDK、Maven 与依赖缓存目录");
        }
        try {
            Path root = Path.of(project.rootPath()).toRealPath();
            Path pom = containedFile(root, request.pomPath());
            if (!pom.getFileName().toString().equals("pom.xml")) throw new IllegalArgumentException("必须选择 pom.xml");
            Path module = pom.getParent();
            Path javaHome = Path.of(settings.javaHome()).toRealPath();
            Path java = javaHome.resolve("bin/java.exe").toRealPath();
            Map<String, String> fingerprints = new TreeMap<>();
            fingerprint(fingerprints, pom);
            fingerprint(fingerprints, java);
            Map<String, String> environment = Map.of("JAVA_HOME", javaHome.toString());
            List<String> command = new ArrayList<>();
            command.add(java.toString());
            List<String> risks;
            if (action.equals("JAVA_JAR")) {
                Path jar = containedFile(module, request.jarPath());
                if (!jar.getFileName().toString().endsWith(".jar") || !jar.startsWith(module.resolve("target"))) {
                    throw new IllegalArgumentException("只能启动所选 Maven 模块 target 下的 JAR");
                }
                fingerprint(fingerprints, jar);
                command.addAll(List.of("-jar", jar.toString()));
                risks = List.of("启动所选 JAR 内的项目代码，可能监听端口、访问网络、数据库及本机文件。",
                        "任务保持运行直到项目退出、取消或达到 24 小时上限；运行中不代表健康检查通过。");
            } else {
                Path maven = Path.of(settings.mavenHome()).toRealPath();
                Path launcher;
                try (var files = Files.list(maven.resolve("boot"))) {
                    launcher = files.filter(p -> p.getFileName().toString().matches("plexus-classworlds-[\\w.\\-]+\\.jar"))
                            .sorted().findFirst().orElseThrow(() -> new IllegalArgumentException("Maven boot 目录缺少 classworlds JAR"));
                }
                Path conf = maven.resolve("bin/m2.conf");
                fingerprint(fingerprints, launcher);
                fingerprint(fingerprints, conf);
                // Invoke Maven's Java entry point. No cmd.exe, shell, wrapper or model-generated command.
                command.addAll(List.of("-Dmaven.home=" + maven, "-Dclassworlds.conf=" + conf,
                        "-Dmaven.multiModuleProjectDirectory=" + module,
                        "-classpath", launcher.toString(), "org.codehaus.plexus.classworlds.launcher.Launcher",
                        "--batch-mode", "--no-transfer-progress", "-f", pom.toString(),
                        "-Dmaven.repo.local=" + settings.cachePath(), action.equals("MAVEN_VERIFY") ? "verify" : "package"));
                risks = List.of("Maven 会执行项目配置中的插件与测试代码，可访问网络及本机文件。",
                        "会下载依赖并写入指定缓存和项目构建目录；不是沙箱执行。",
                        "审批锁定计划、选定 POM 与启动器；不冻结整个仓库或外部依赖。执行前请确认项目来源可信。");
            }
            return new ExecutionPlan(project.id(), request.goal().trim(), action, root.relativize(pom).toString(),
                    module.toString(), List.copyOf(command), environment, Collections.unmodifiableMap(fingerprints),
                    action.equals("JAVA_JAR") ? 86400 : 900, risks,
                    action.equals("JAVA_JAR") ? List.of("进程退出码和运行日志；健康接口仍需单独验证")
                            : List.of("进程正常退出且退出码为 0；保留 Maven 日志"));
        } catch (IOException exception) {
            throw new IllegalArgumentException("项目或工具路径不可读取，请重新选择并生成计划");
        }
    }

    public void verify(ExecutionPlan plan) {
        try {
            if (!Files.isDirectory(Path.of(plan.workingDirectory()))) throw new IllegalArgumentException("项目目录已失效");
            for (var entry : plan.fingerprints().entrySet()) {
                if (!digest(Path.of(entry.getKey())).equals(entry.getValue())) {
                    throw new IllegalArgumentException("计划输入已变更，请重新生成并审批计划");
                }
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("计划输入不可读取，请重新生成并审批计划");
        }
    }

    public static Path containedFile(Path root, String relative) throws IOException {
        if (relative == null || relative.isBlank() || Path.of(relative).isAbsolute()) {
            throw new IllegalArgumentException("必须提供项目内相对路径");
        }
        Path file = root.resolve(relative).normalize();
        if (!file.startsWith(root) || !Files.isRegularFile(file)) throw new IllegalArgumentException("文件不存在或超出项目范围");
        Path real = file.toRealPath();
        if (!real.startsWith(root.toRealPath())) throw new IllegalArgumentException("文件链接超出项目范围");
        return real;
    }

    public static String digest(Path file) throws IOException {
        try (InputStream stream = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int count;
            while ((count = stream.read(buffer)) != -1) digest.update(buffer, 0, count);
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }

    private void fingerprint(Map<String, String> entries, Path file) throws IOException {
        entries.put(file.toRealPath().toString(), digest(file));
    }
}
