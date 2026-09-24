CREATE TABLE IF NOT EXISTS remediation_recommendations (
    id                      VARCHAR(26)  NOT NULL PRIMARY KEY,
    scan_id                 VARCHAR(26)  NOT NULL,
    dependency_id           VARCHAR(26)  NOT NULL,
    current_version         VARCHAR(100) NOT NULL,
    recommended_version     VARCHAR(100) NOT NULL,
    upgrade_type            VARCHAR(20)  NOT NULL,
    breaking_change_summary TEXT,
    reason                  TEXT         NOT NULL,
    confidence              VARCHAR(20)  NOT NULL,
    CONSTRAINT uq_remediation_scan_dependency UNIQUE (scan_id, dependency_id),
    CONSTRAINT fk_remediation_scan FOREIGN KEY (scan_id) REFERENCES scans(id),
    CONSTRAINT fk_remediation_dependency FOREIGN KEY (dependency_id) REFERENCES dependencies(id)
);
CREATE INDEX IF NOT EXISTS idx_remediation_scan ON remediation_recommendations(scan_id);
