-- V1: Initial schema — projects, scans, dependencies, scan_dependencies

CREATE TABLE projects
(
    id             VARCHAR(26)  NOT NULL,
    name           VARCHAR(255) NOT NULL,
    repository_url VARCHAR(512) NOT NULL,
    default_branch VARCHAR(255) NOT NULL DEFAULT 'main',
    created_at     TIMESTAMP    NOT NULL,
    updated_at     TIMESTAMP    NOT NULL,
    version        INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT pk_projects PRIMARY KEY (id)
);

CREATE TABLE scans
(
    id            VARCHAR(26)  NOT NULL,
    project_id    VARCHAR(26)  NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    commit_sha    VARCHAR(40),
    branch        VARCHAR(255),
    error_message TEXT,
    started_at    TIMESTAMP,
    completed_at  TIMESTAMP,
    created_at    TIMESTAMP    NOT NULL,
    updated_at    TIMESTAMP    NOT NULL,
    version       INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT pk_scans PRIMARY KEY (id),
    CONSTRAINT fk_scans_project FOREIGN KEY (project_id) REFERENCES projects (id)
);

CREATE TABLE dependencies
(
    id          VARCHAR(26)  NOT NULL,
    group_id    VARCHAR(255) NOT NULL,
    artifact_id VARCHAR(255) NOT NULL,
    version     VARCHAR(100) NOT NULL,
    ecosystem   VARCHAR(50)  NOT NULL DEFAULT 'MAVEN',
    created_at  TIMESTAMP    NOT NULL,
    updated_at  TIMESTAMP    NOT NULL,
    version_col INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT pk_dependencies PRIMARY KEY (id),
    CONSTRAINT uq_dependencies UNIQUE (group_id, artifact_id, version, ecosystem)
);

CREATE TABLE scan_dependencies
(
    scan_id       VARCHAR(26) NOT NULL,
    dependency_id VARCHAR(26) NOT NULL,
    scope         VARCHAR(20) NOT NULL,
    direct        BOOLEAN     NOT NULL,
    CONSTRAINT pk_scan_dependencies PRIMARY KEY (scan_id, dependency_id),
    CONSTRAINT fk_sd_scan FOREIGN KEY (scan_id) REFERENCES scans (id),
    CONSTRAINT fk_sd_dependency FOREIGN KEY (dependency_id) REFERENCES dependencies (id)
);

-- Indexes
CREATE INDEX idx_scans_project_id ON scans (project_id);
CREATE INDEX idx_scan_deps_scan_id ON scan_dependencies (scan_id);
CREATE INDEX idx_scan_deps_dependency_id ON scan_dependencies (dependency_id);
