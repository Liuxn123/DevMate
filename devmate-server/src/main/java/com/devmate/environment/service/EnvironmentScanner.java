package com.devmate.environment.service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.devmate.environment.model.EnvironmentSnapshot;
import com.devmate.environment.model.EnvironmentToolResult;

import org.springframework.stereotype.Service;

@Service
public class EnvironmentScanner {

    private static final int MAX_OUTPUT_BYTES = 8 * 1024;
    private static final long COMMAND_TIMEOUT_SECONDS = 4;
    private static final Pattern JAVA_VERSION = Pattern.compile("(?i)(?:openjdk|java) version \\\"?([^\\s\\\"]+)");
    private static final Pattern MAVEN_VERSION = Pattern.compile("(?i)Apache Maven\\s+([^\\s]+)");
    private static final Pattern GIT_VERSION = Pattern.compile("(?i)git version\\s+([^\\s]+)");

    public EnvironmentSnapshot scan() {
        List<EnvironmentToolResult> tools = List.of(
                inspectJava(),
                inspectMaven(),
                inspectGit());
        return new EnvironmentSnapshot(
                Instant.now().toString(),
                tools,
                "仅调用 java -version、mvn -version、git --version；不会运行项目脚本或修改环境配置。");
    }

    private EnvironmentToolResult inspectJava() {
        Optional<Path> executable = findExecutable("java.exe", List.of(
                homeBin("JAVA_HOME", "java.exe")));
        if (executable.isEmpty()) {
            return missing("JDK", "PATH 和 JAVA_HOME 中未找到 java.exe");
        }
        return probe("JDK", executable.get(), List.of("-version"), JAVA_VERSION);
    }

    private EnvironmentToolResult inspectMaven() {
        Optional<Path> executable = findExecutable("mvn.cmd", List.of(
                homeBin("MAVEN_HOME", "mvn.cmd"),
                homeBin("M2_HOME", "mvn.cmd")));
        if (executable.isEmpty()) {
            executable = findExecutable("mvn.bat", List.of(
                    homeBin("MAVEN_HOME", "mvn.bat"),
                    homeBin("M2_HOME", "mvn.bat")));
        }
        if (executable.isEmpty()) {
            return missing("Maven", "PATH、MAVEN_HOME 和 M2_HOME 中未找到 Maven 启动器");
        }

        Path launcher = executable.get();
        try {
            String commandProcessor = System.getenv().getOrDefault("SystemRoot", "C:\\Windows")
                    + "\\System32\\cmd.exe";
            ProcessBuilder builder = new ProcessBuilder(commandProcessor, "/d", "/c", "mvn -version");
            builder.directory(Path.of(System.getProperty("java.io.tmpdir")).toFile());
            String currentPath = builder.environment().getOrDefault("PATH", "");
            String launcherDirectory = launcher.getParent().toString();
            builder.environment().put("PATH", launcherDirectory + File.pathSeparator + currentPath);
            return runProbe("Maven", launcher, builder, MAVEN_VERSION);
        } catch (RuntimeException exception) {
            return error("Maven", launcher, "无法准备版本查询：" + safeMessage(exception));
        }
    }

    private EnvironmentToolResult inspectGit() {
        Optional<Path> executable = findExecutable("git.exe", List.of(
                envCandidate("GIT_HOME", "cmd", "git.exe"),
                envCandidate("GIT_HOME", "bin", "git.exe")));
        if (executable.isEmpty()) {
            return missing("Git", "PATH 和 GIT_HOME 中未找到 git.exe");
        }
        return probe("Git", executable.get(), List.of("--version"), GIT_VERSION);
    }

    private EnvironmentToolResult probe(String name, Path executable, List<String> arguments, Pattern versionPattern) {
        try {
            List<String> command = new ArrayList<>();
            command.add(executable.toString());
            command.addAll(arguments);
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.directory(Path.of(System.getProperty("java.io.tmpdir")).toFile());
            return runProbe(name, executable, builder, versionPattern);
        } catch (RuntimeException exception) {
            return error(name, executable, "无法准备版本查询：" + safeMessage(exception));
        }
    }

    private EnvironmentToolResult runProbe(
            String name, Path executable, ProcessBuilder builder, Pattern versionPattern) {
        Process process = null;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        Thread outputReader = null;
        try {
            builder.redirectErrorStream(true);
            process = builder.start();
            Process runningProcess = process;
            InputStream stream = runningProcess.getInputStream();
            outputReader = new Thread(() -> copyBounded(stream, captured), "devmate-tool-version-reader");
            outputReader.setDaemon(true);
            outputReader.start();

            if (!process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return error(name, executable, "版本查询超时（" + COMMAND_TIMEOUT_SECONDS + " 秒）");
            }
            outputReader.join(1000);
            String output = captured.toString(StandardCharsets.UTF_8).trim();
            if (process.exitValue() != 0) {
                return error(name, executable, concise(output, "版本查询退出码：" + process.exitValue()));
            }
            Matcher matcher = versionPattern.matcher(output);
            if (!matcher.find()) {
                return new EnvironmentToolResult(name, "AVAILABLE", "未知", executable.toString(),
                        concise(output, "命令成功，但未能解析版本文本"));
            }
            return new EnvironmentToolResult(
                    name, "AVAILABLE", matcher.group(1), executable.toString(), concise(output, "版本查询成功"));
        } catch (IOException exception) {
            return error(name, executable, "版本查询失败：" + safeMessage(exception));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            if (process != null) {
                process.destroyForcibly();
            }
            return error(name, executable, "版本查询被中断");
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            if (outputReader != null) {
                outputReader.interrupt();
            }
        }
    }

    private static void copyBounded(InputStream stream, ByteArrayOutputStream output) {
        try (stream) {
            byte[] buffer = new byte[1024];
            int remaining = MAX_OUTPUT_BYTES;
            int read;
            while (remaining > 0 && (read = stream.read(buffer, 0, Math.min(buffer.length, remaining))) >= 0) {
                output.write(buffer, 0, read);
                remaining -= read;
            }
        } catch (IOException ignored) {
            // The process may have timed out and been terminated while its output was being read.
        }
    }

    private Optional<Path> findExecutable(String fileName, List<Optional<Path>> preferred) {
        for (Optional<Path> candidate : preferred) {
            if (candidate.isPresent() && isExecutableFile(candidate.get())) {
                return candidate.map(path -> path.toAbsolutePath().normalize());
            }
        }

        String pathValue = System.getenv().getOrDefault("PATH", "");
        for (String entry : pathValue.split(Pattern.quote(File.pathSeparator))) {
            String directory = stripQuotes(entry.trim());
            if (directory.isBlank()) {
                continue;
            }
            try {
                Path candidate = Path.of(directory).resolve(fileName);
                if (isExecutableFile(candidate)) {
                    return Optional.of(candidate.toAbsolutePath().normalize());
                }
            } catch (RuntimeException ignored) {
                // Ignore malformed PATH entries and continue with the remaining entries.
            }
        }
        return Optional.empty();
    }

    private static Optional<Path> homeBin(String variableName, String fileName) {
        String home = System.getenv(variableName);
        if (home == null || home.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Path.of(stripQuotes(home.trim())).resolve("bin").resolve(fileName));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private static Optional<Path> envCandidate(String variableName, String child, String fileName) {
        String home = System.getenv(variableName);
        if (home == null || home.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Path.of(stripQuotes(home.trim())).resolve(child).resolve(fileName));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private static boolean isExecutableFile(Path path) {
        return Files.isRegularFile(path) && Files.isReadable(path);
    }

    private static EnvironmentToolResult missing(String name, String detail) {
        return new EnvironmentToolResult(name, "NOT_FOUND", null, null, detail);
    }

    private static EnvironmentToolResult error(String name, Path path, String detail) {
        return new EnvironmentToolResult(name, "ERROR", null, path.toAbsolutePath().normalize().toString(), detail);
    }

    private static String stripQuotes(String value) {
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static String concise(String output, String fallback) {
        if (output == null || output.isBlank()) {
            return fallback;
        }
        String normalized = output.replaceAll("[\\r\\n]+", " · ").trim();
        return normalized.length() > 500 ? normalized.substring(0, 500) : normalized;
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }
}
