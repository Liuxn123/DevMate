package com.devmate.project.repository;

import java.util.List;
import java.util.Optional;

import com.devmate.project.model.ProjectRecord;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class ProjectRepository {

    private static final RowMapper<ProjectRecord> PROJECT_ROW_MAPPER = (resultSet, rowNumber) ->
            new ProjectRecord(
                    resultSet.getString("id"),
                    resultSet.getString("name"),
                    resultSet.getString("source_type"),
                    resultSet.getString("repo_url"),
                    resultSet.getString("root_path"),
                    resultSet.getString("project_type"),
                    resultSet.getString("created_at"));

    private final JdbcTemplate jdbcTemplate;

    public ProjectRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ProjectRecord> findAll() {
        return jdbcTemplate.query(
                "SELECT id, name, source_type, repo_url, root_path, project_type, created_at "
                        + "FROM dm_project ORDER BY created_at DESC, id ASC",
                PROJECT_ROW_MAPPER);
    }

    public Optional<ProjectRecord> findByRootPath(String rootPath) {
        return jdbcTemplate.query(
                        "SELECT id, name, source_type, repo_url, root_path, project_type, created_at "
                                + "FROM dm_project WHERE root_path = ?",
                        PROJECT_ROW_MAPPER,
                        rootPath)
                .stream()
                .findFirst();
    }

    public Optional<ProjectRecord> findById(String id) {
        return jdbcTemplate.query(
                        "SELECT id, name, source_type, repo_url, root_path, project_type, created_at "
                                + "FROM dm_project WHERE id = ?",
                        PROJECT_ROW_MAPPER,
                        id)
                .stream()
                .findFirst();
    }

    public void save(ProjectRecord project) {
        jdbcTemplate.update(
                "INSERT INTO dm_project "
                        + "(id, name, source_type, repo_url, root_path, project_type, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                project.id(),
                project.name(),
                project.sourceType(),
                project.repositoryUrl(),
                project.rootPath(),
                project.projectType(),
                project.createdAt());
    }

    public void updateProjectType(String id, String projectType) {
        jdbcTemplate.update(
                "UPDATE dm_project SET project_type = ? WHERE id = ?",
                projectType,
                id);
    }
}
