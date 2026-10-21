package com.devmate.task.service;

import com.devmate.task.model.TaskRecord;
import com.devmate.task.repository.TaskRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.*;

@Service
public class TaskExecutor {
    private final TaskRepository tasks;
    private final PlanPolicy policy;
    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
    private final ConcurrentMap<String, Process> processes = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> activeProjects = new ConcurrentHashMap<>();
    private final Semaphore slots = new Semaphore(2);
    private volatile boolean closing;

    public TaskExecutor(TaskRepository tasks, PlanPolicy policy) { this.tasks = tasks; this.policy = policy; }

    @PostConstruct
    public void recover() { tasks.recover(); }

    public synchronized void start(TaskRecord task) {
        if (closing || !slots.tryAcquire()) throw new ResponseStatusException(HttpStatus.CONFLICT, "已有两个任务运行，请稍后再试");
        if (activeProjects.putIfAbsent(task.plan().projectId(), task.id()) != null) {
            slots.release();
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该项目已有任务正在运行");
        }
        try {
            if (!tasks.transition(task.id(), "APPROVED", "RUNNING", null, "正在准备进程")) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "任务未审批或已经执行");
            }
            workers.submit(() -> run(task));
        } catch (RuntimeException exception) {
            release(task);
            if (exception instanceof RejectedExecutionException) {
                tasks.transition(task.id(), "RUNNING", "INTERRUPTED", null, "执行器正在关闭");
            }
            throw exception;
        }
    }

    private void run(TaskRecord task) {
        Process process = null;
        Future<?> reader = null;
        try {
            policy.verify(task.plan());
            ProcessBuilder builder = new ProcessBuilder(task.plan().command());
            builder.directory(Path.of(task.plan().workingDirectory()).toFile()).redirectErrorStream(true);
            // Do not inherit JVM injection options from the desktop process.
            builder.environment().keySet().removeIf(key -> java.util.Set.of("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS", "CLASSPATH", "MAVEN_OPTS", "MAVEN_ARGS").contains(key.toUpperCase(java.util.Locale.ROOT)));
            builder.environment().putAll(task.plan().environment());
            synchronized (this) {
                if (closing || !tasks.get(task.id()).status().equals("RUNNING")) {
                    tasks.transition(task.id(), "CANCELLING", "CANCELLED", null, "启动前已取消");
                    return;
                }
                process = builder.start();
                processes.put(task.id(), process);
            }
            tasks.log(task.id(), "进程已启动，PID=" + process.pid() + "；运行状态不代表健康验证通过。");
            Process running = process;
            reader = workers.submit(() -> capture(task.id(), running));
            boolean finished = process.waitFor(task.plan().timeoutSeconds(), TimeUnit.SECONDS);
            if (!finished) terminate(process);
            try { reader.get(3, TimeUnit.SECONDS); } catch (TimeoutException ignored) { reader.cancel(true); }
            String current = tasks.get(task.id()).status();
            if (current.equals("CANCELLING")) {
                tasks.transition(task.id(), current, "CANCELLED", null, "用户取消，已请求终止进程树");
            } else if (!finished) {
                tasks.transition(task.id(), "RUNNING", "TIMED_OUT", null, "达到运行时间上限，已请求终止进程树");
            } else {
                int code = process.exitValue();
                tasks.transition(task.id(), "RUNNING", code == 0 ? "SUCCEEDED" : "FAILED", code,
                        "进程已退出，退出码=" + code + (task.plan().action().equals("JAVA_JAR") ? "；未执行 HTTP 健康验证" : ""));
            }
            // A cancellation may race with the terminal update above.
            tasks.transition(task.id(), "CANCELLING", "CANCELLED", null, "用户取消，已请求终止进程树");
        } catch (Exception exception) {
            if (process != null) terminate(process);
            String current = tasks.get(task.id()).status();
            tasks.transition(task.id(), current, current.equals("CANCELLING") ? "CANCELLED" : "FAILED", null,
                    "执行中断：" + exception.getClass().getSimpleName() + "；检查项目、工具路径和日志后重新生成计划");
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
        } finally {
            if (process != null && process.isAlive()) terminate(process);
            if (reader != null) reader.cancel(true);
            processes.remove(task.id());
            release(task);
        }
    }

    private void capture(String id, Process process) {
        // Drain even after the 1 MiB storage limit; long lines are split into bounded chunks.
        try (var input = new InputStreamReader(process.getInputStream(), Charset.defaultCharset())) {
            char[] buffer = new char[2048];
            int count;
            int saved = 0;
            while ((count = input.read(buffer)) != -1) {
                if (saved < 1024 * 1024) {
                    int keep = Math.min(count, 1024 * 1024 - saved);
                    tasks.log(id, new String(buffer, 0, keep));
                    saved += keep;
                    if (saved >= 1024 * 1024) tasks.log(id, "\n[日志达到 1 MiB 字符上限，后续输出不再保存]\n");
                }
            }
        } catch (java.io.IOException exception) {
            // Pipe closes during cancellation/shutdown. A live process losing its log is a failure.
            if (process.isAlive()) throw new IllegalStateException("执行日志读取中断", exception);
        }
    }

    public synchronized TaskRecord cancel(String id) {
        var task = tasks.get(id);
        if (task.status().equals("RUNNING")) {
            tasks.transition(id, "RUNNING", "CANCELLING", null, "正在终止进程树");
            Process process = processes.get(id);
            if (process != null) terminate(process);
        } else if (!task.status().equals("CANCELLING")) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "只有运行中的任务可以取消");
        }
        return tasks.get(id);
    }

    private void release(TaskRecord task) {
        activeProjects.remove(task.plan().projectId(), task.id());
        slots.release();
    }

    private void terminate(Process process) {
        // Only descendants of the process owned by this executor are targeted.
        var children = process.descendants().toList();
        for (int i = children.size() - 1; i >= 0; i--) children.get(i).destroyForcibly();
        process.destroyForcibly();
    }

    @PreDestroy
    public synchronized void close() {
        closing = true;
        for (Map.Entry<String, Process> entry : processes.entrySet()) terminate(entry.getValue());
        workers.shutdownNow();
    }
}
