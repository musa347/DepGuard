-- V5: Vulnerability records from OSV API (one per advisory) and the join to scan dependencies.
CREATE TABLE IF NOT EXISTS vulnerability_records (
    id                      VARCHAR(26)   NOT NULL PRIMARY KEY,
    osv_id                  VARCHAR(100)  NOT NULL UNIQUE,
    summary                 TEXT,
    severity                VARCHAR(20)   NOT NULL,
    cvss_score              NUMERIC(4,1),
    cvss_vector             VARCHAR(200),
    published_at            TIMESTAMPTZ,
    data_source_fetched_at  TIMESTAMPTZ   NOT NULL,
    version_col             INT           NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_vuln_osv_id ON vulnerability_records(osv_id);

CREATE TABLE IF NOT EXISTS dependency_vulnerabilities (
    dependency_id           VARCHAR(26)   NOT NULL,
    scan_id                 VARCHAR(26)   NOT NULL,
    vulnerability_record_id VARCHAR(26)   NOT NULL,
    PRIMARY KEY (dependency_id, scan_id, vulnerability_record_id),
    CONSTRAINT fk_depvuln_dependency  FOREIGN KEY (dependency_id) REFERENCES dependencies(id),
    CONSTRAINT fk_depvuln_scan        FOREIGN KEY (scan_id)        REFERENCES scans(id),
    CONSTRAINT fk_depvuln_vuln_record FOREIGN KEY (vulnerability_record_id) REFERENCES vulnerability_records(id)
);
CREATE INDEX IF NOT EXISTS idx_depvuln_scan ON dependency_vulnerabilities(scan_id);
