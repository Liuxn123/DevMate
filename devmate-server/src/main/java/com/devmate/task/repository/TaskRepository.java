package com.devmate.task.repository;

import com.devmate.task.model.ExecutionPlan;
import com.devmate.task.model.TaskRecord;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.Instant;
import java.util.*;

@Repository
public class TaskRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public TaskRepository(JdbcTemplate jdbc, ObjectMapper mapper) { this.jdbc = jdbc; this.mapper = mapper; }

    public String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("任务序列化失败", exception); }
    }

    public TaskRecord create(ExecutionPlan plan) {
        String id = UUID.randomUUID().toString();
        String payload = json(plan);
        String hash;
        try {
            hash = HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
        String now = Instant.now().toString();
        jdbc.update("INSERT INTO dm_task(id, project_id, version, content_hash, plan_json, status, created_at, updated_at) VALUES (?, ?, 1, ?, ?, 'PENDING_APPROVAL', ?, ?)",
                id, plan.projectId(), hash, payload, now, now);
        return get(id);
    }

    public List<TaskRecord> list() {
        return jdbc.query("SELECT * FROM dm_task ORDER BY created_at DESC LIMIT 200", (rs, row) -> read(rs));
    }

    public TaskRecord get(String id) {
        return jdbc.query("SELECT * FROM dm_task WHERE id=?", (rs, row) -> read(rs), id).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在"));
    }

    private TaskRecord read(java.sql.ResultSet rs) throws java.sql.SQLException {
        try {
            return new TaskRecord(rs.getString("id"), rs.getInt("version"), rs.getString("content_hash"),
                    mapper.readValue(rs.getString("plan_json"), ExecutionPlan.class), rs.getString("status"),
                    rs.getString("created_at"), rs.getString("updated_at"), rs.getString("approved_at"),
                    (Integer) rs.getObject("exit_code"), rs.getString("result"));
        } catch (JsonProcessingException exception) { throw new IllegalStateException("任务记录损坏", exception); }
    }

    public boolean approve(String id, int version, String hash) {
        String now = Instant.now().toString();
        return jdbc.update("UPDATE dm_task SET status='APPROVED', approved_at=?, updated_at=? WHERE id=? AND version=? AND content_hash=? AND status='PENDING_APPROVAL'",
                now, now, id, version, hash) == 1;
    }

    public boolean transition(String id, String from, String to, Integer code, String result) {
        return jdbc.update("UPDATE dm_task SET status=?, exit_code=?, result=?, updated_at=? WHERE id=? AND status=?",
                to, code, result, Instant.now().toString(), id, from) == 1;
    }

    public void log(String id, String content) {
        jdbc.update("INSERT INTO dm_task_log(task_id, recorded_at, content) VALUES (?, ?, ?)", id, Instant.now().toString(), content);
    }

    public List<Map<String, Object>> logs(String id, long after) {
        get(id);
        return jdbc.queryForList("SELECT sequence, recorded_at AS recordedAt, content FROM dm_task_log WHERE task_id=? AND sequence>? ORDER BY sequence LIMIT 200", id, Math.max(0, after));
    }

    public void recover() {
        jdbc.update("UPDATE dm_task SET status='INTERRUPTED', result='后端重启导致执行中断；未自动恢复，也未重跑命令。请检查遗留进程。', updated_at=? WHERE status IN ('RUNNING', 'CANCELLING')", Instant.now().toString());
    }
}
