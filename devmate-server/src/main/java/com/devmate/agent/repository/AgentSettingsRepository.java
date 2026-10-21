package com.devmate.agent.repository;

import com.devmate.agent.model.AgentSettings;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 集中维护单条 Agent 设置的 SQLite 读写。 */
@Repository
public class AgentSettingsRepository {

    private final JdbcTemplate jdbc;

    public AgentSettingsRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        jdbc.update("INSERT OR IGNORE INTO agent_settings(id, base_url, model, api_key) VALUES (1, ?, ?, '')",
                "https://api.deepseek.com", "deepseek-chat");
    }

    public AgentSettings get() {
        return jdbc.queryForObject("SELECT base_url, model, api_key FROM agent_settings WHERE id = 1",
                (rs, rowNum) -> new AgentSettings(
                        rs.getString("base_url"), rs.getString("model"), rs.getString("api_key")));
    }

    public void save(AgentSettings settings) {
        jdbc.update("UPDATE agent_settings SET base_url = ?, model = ?, api_key = ? WHERE id = 1",
                settings.baseUrl(), settings.model(), settings.apiKey());
    }
}
