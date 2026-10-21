package com.devmate.task.repository;

import com.devmate.task.model.ExecutionPlan;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TaskRepositoryTest {
    @TempDir Path directory;
    TaskRepository tasks;

    @BeforeEach void setup() {
        var source = new DriverManagerDataSource("jdbc:sqlite:" + directory.resolve("test.db"));
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V1__create_project_table.sql"),
                new ClassPathResource("db/migration/V3__create_tasks_and_conversations.sql")).execute(source);
        var jdbc = new JdbcTemplate(source);
        jdbc.update("INSERT INTO dm_project VALUES ('project', 'Test', 'LOCAL', NULL, '/test', 'UNKNOWN', 'now')");
        tasks = new TaskRepository(jdbc, new ObjectMapper());
    }

    private ExecutionPlan plan() {
        return new ExecutionPlan("project", "build", "MAVEN_PACKAGE", "pom.xml", "/test",
                List.of("java", "fixed"), Map.of(), Map.of(), 900, List.of("risk"), List.of("exit zero"));
    }

    @Test void approvalBindsHashAndCannotBeReused() {
        var task = tasks.create(plan());
        assertFalse(tasks.approve(task.id(), task.version(), "wrong hash"));
        assertFalse(tasks.approve(task.id(), 2, task.contentHash()));
        assertTrue(tasks.approve(task.id(), task.version(), task.contentHash()));
        assertFalse(tasks.approve(task.id(), task.version(), task.contentHash()));
        assertTrue(tasks.transition(task.id(), "APPROVED", "RUNNING", null, "start"));
        assertFalse(tasks.transition(task.id(), "APPROVED", "RUNNING", null, "duplicate"));
    }

    @Test void rejectedPlanCannotExecuteAndRestartDoesNotRetry() {
        var rejected = tasks.create(plan());
        assertTrue(tasks.transition(rejected.id(), "PENDING_APPROVAL", "REJECTED", null, "no"));
        assertFalse(tasks.approve(rejected.id(), 1, rejected.contentHash()));
        var running = tasks.create(plan());
        tasks.approve(running.id(), 1, running.contentHash());
        tasks.transition(running.id(), "APPROVED", "RUNNING", null, "start");
        tasks.recover();
        assertEquals("INTERRUPTED", tasks.get(running.id()).status());
        assertEquals("REJECTED", tasks.get(rejected.id()).status());
    }

    @Test void logsHaveStableCursorAndPlanRoundTrips() {
        var task = tasks.create(plan());
        tasks.log(task.id(), "first");
        tasks.log(task.id(), "second");
        var logs = tasks.logs(task.id(), 0);
        assertEquals(2, logs.size());
        long cursor = ((Number) logs.getFirst().get("sequence")).longValue();
        assertEquals("second", tasks.logs(task.id(), cursor).getFirst().get("content"));
        assertEquals(plan(), tasks.get(task.id()).plan());
    }
}
