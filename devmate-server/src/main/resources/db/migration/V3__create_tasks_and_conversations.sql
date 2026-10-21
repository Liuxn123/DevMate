CREATE TABLE dm_project_environment (
    project_id TEXT PRIMARY KEY REFERENCES dm_project(id),
    java_home TEXT NOT NULL,
    maven_home TEXT NOT NULL,
    cache_path TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE TABLE dm_task (
    id TEXT PRIMARY KEY,
    project_id TEXT NOT NULL REFERENCES dm_project(id),
    version INTEGER NOT NULL,
    content_hash TEXT NOT NULL,
    plan_json TEXT NOT NULL,
    status TEXT NOT NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    approved_at TEXT,
    exit_code INTEGER,
    result TEXT
);
CREATE INDEX idx_dm_task_project ON dm_task(project_id, created_at);
CREATE TABLE dm_task_log (
    sequence INTEGER PRIMARY KEY AUTOINCREMENT,
    task_id TEXT NOT NULL REFERENCES dm_task(id),
    recorded_at TEXT NOT NULL,
    content TEXT NOT NULL
);
CREATE INDEX idx_dm_task_log_task ON dm_task_log(task_id, sequence);

CREATE TABLE dm_conversation (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    project_id TEXT,
    pom_path TEXT,
    created_at TEXT NOT NULL
);
CREATE TABLE dm_conversation_message (
    sequence INTEGER PRIMARY KEY AUTOINCREMENT,
    conversation_id TEXT NOT NULL REFERENCES dm_conversation(id),
    role TEXT NOT NULL,
    payload TEXT NOT NULL,
    created_at TEXT NOT NULL
);
