package com.devmate.agent.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.Instant;
import java.util.*;

@Repository
public class ConversationRepository {
    private final JdbcTemplate jdbc;
    public ConversationRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Map<String, Object>> list() {
        return jdbc.queryForList("SELECT id, title, project_id AS projectId, pom_path AS pomPath, created_at AS createdAt FROM dm_conversation ORDER BY created_at DESC LIMIT 100");
    }

    public Map<String, Object> get(String id) {
        return jdbc.queryForList("SELECT id, title, project_id AS projectId, pom_path AS pomPath, created_at AS createdAt FROM dm_conversation WHERE id=?", id)
                .stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在"));
    }

    public Map<String, Object> create(String title, String project, String pom) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO dm_conversation(id,title,project_id,pom_path,created_at) VALUES (?,?,?,?,?)", id, title, project, pom, Instant.now().toString());
        return get(id);
    }

    public List<Map<String, Object>> messages(String id) {
        get(id);
        return jdbc.queryForList("SELECT sequence, role, payload, created_at AS createdAt FROM dm_conversation_message WHERE conversation_id=? ORDER BY sequence", id);
    }

    @Transactional
    public void appendPair(String id, String question, String answer) {
        String now = Instant.now().toString();
        jdbc.update("UPDATE dm_conversation SET title=? WHERE id=? AND NOT EXISTS (SELECT 1 FROM dm_conversation_message WHERE conversation_id=?)",
                question.substring(0, Math.min(question.length(), 60)), id, id);
        jdbc.update("INSERT INTO dm_conversation_message(conversation_id,role,payload,created_at) VALUES (?,'user',?,?)", id, question, now);
        jdbc.update("INSERT INTO dm_conversation_message(conversation_id,role,payload,created_at) VALUES (?,'assistant',?,?)", id, answer, now);
    }
}
