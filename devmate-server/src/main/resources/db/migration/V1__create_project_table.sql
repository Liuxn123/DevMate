CREATE TABLE dm_project (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    source_type TEXT NOT NULL,
    repo_url TEXT,
    root_path TEXT NOT NULL UNIQUE,
    project_type TEXT NOT NULL,
    created_at TEXT NOT NULL
);

CREATE INDEX idx_dm_project_created_at ON dm_project (created_at);
