-- V4: EOL enrichment records (one per dependency per scan).
-- The table is created here; V1__init.sql did not include it (deviation noted in V3).
CREATE TABLE IF NOT EXISTS eol_records (
    dependency_id  VARCHAR(26)  NOT NULL,
    scan_id         VARCHAR(26)  NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    eol_date        DATE,
    source          VARCHAR(20)  NOT NULL,
    data_source_fetched_at TIMESTAMPTZ NOT NULL,
    version_col    INT           NOT NULL DEFAULT 0,
    PRIMARY KEY (dependency_id, scan_id),
    CONSTRAINT fk_eol_dependency FOREIGN KEY (dependency_id) REFERENCES dependencies(id),
    CONSTRAINT fk_eol_scan FOREIGN KEY (scan_id) REFERENCES scans(id)
);
CREATE INDEX IF NOT EXISTS idx_eol_records_scan ON eol_records(scan_id);
CREATE INDEX IF NOT EXISTS idx_eol_records_status ON eol_records(status);
