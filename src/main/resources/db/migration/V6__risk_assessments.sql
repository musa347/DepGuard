-- V6: Dependency Risk Heuristic assessment for every dependency in a scan.
CREATE TABLE IF NOT EXISTS risk_assessments (
    scan_id         VARCHAR(26) NOT NULL,
    dependency_id   VARCHAR(26) NOT NULL,
    risk_level      VARCHAR(20) NOT NULL,
    heuristic_score INT         NOT NULL,
    confidence      VARCHAR(20) NOT NULL,
    reasons         JSONB       NOT NULL,
    version_col     INT         NOT NULL DEFAULT 0,
    PRIMARY KEY (scan_id, dependency_id),
    CONSTRAINT fk_risk_scan FOREIGN KEY (scan_id) REFERENCES scans(id),
    CONSTRAINT fk_risk_dependency FOREIGN KEY (dependency_id) REFERENCES dependencies(id)
);
CREATE INDEX IF NOT EXISTS idx_risk_assessments_scan ON risk_assessments(scan_id);
CREATE INDEX IF NOT EXISTS idx_risk_assessments_level ON risk_assessments(risk_level);
