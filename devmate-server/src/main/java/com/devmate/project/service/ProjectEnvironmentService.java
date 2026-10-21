package com.devmate.project.service;

import java.nio.file.Files;
import java.nio.file.Path;

import com.devmate.project.dto.ProjectEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ProjectEnvironmentService {
    private final JdbcTemplate jdbc;
    private final ProjectService projects;

    public ProjectEnvironmentService(JdbcTemplate jdbc, ProjectService projects) {
        this.jdbc = jdbc;
        this.projects = projects;
    }

    public ProjectEnvironment get(String projectId) {
        projects.getProject(projectId);
        return jdbc.query("SELECT java_home, maven_home, cache_path FROM dm_project_environment WHERE project_id = ?",
                (rs, row) -> new ProjectEnvironment(rs.getString(1), rs.getString(2), rs.getString(3)), projectId)
                .stream().findFirst().orElse(new ProjectEnvironment("", "", ""));
    }

    public ProjectEnvironment save(String projectId, ProjectEnvironment request) {
        projects.getProject(projectId);
        Path javaHome = existingDirectory(request.javaHome());
        Path mavenHome = existingDirectory(request.mavenHome());
        if (!Files.isRegularFile(javaHome.resolve("bin/java.exe"))
                || !Files.isRegularFile(javaHome.resolve("bin/javac.exe"))) {
            throw new IllegalArgumentException("JDK 目录必须包含 bin/java.exe 和 bin/javac.exe");
        }
        if (!Files.isRegularFile(mavenHome.resolve("bin/m2.conf"))) {
            throw new IllegalArgumentException("Maven 目录必须包含 bin/m2.conf");
        }
        Path cache = Path.of(request.cachePath().trim());
        if (!cache.isAbsolute()) throw new IllegalArgumentException("依赖缓存目录必须是绝对路径");
        ProjectEnvironment value = new ProjectEnvironment(javaHome.toString(), mavenHome.toString(), cache.normalize().toString());
        jdbc.update("""
                INSERT INTO dm_project_environment(project_id, java_home, maven_home, cache_path, updated_at)
                VALUES (?, ?, ?, ?, ?) ON CONFLICT(project_id) DO UPDATE SET
                java_home=excluded.java_home, maven_home=excluded.maven_home,
                cache_path=excluded.cache_path, updated_at=excluded.updated_at
                """, projectId, value.javaHome(), value.mavenHome(), value.cachePath(), java.time.Instant.now().toString());
        return value;
    }

    private Path existingDirectory(String value) {
        try {
            Path path = Path.of(value.trim());
            if (!path.isAbsolute() || !Files.isDirectory(path)) throw new IllegalArgumentException("请选择存在的绝对目录");
            return path.toRealPath();
        } catch (java.io.IOException exception) {
            throw new IllegalArgumentException("目录不可读");
        }
    }
}
