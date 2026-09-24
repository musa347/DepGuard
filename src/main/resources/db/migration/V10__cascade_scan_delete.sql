-- V10: Add ON DELETE CASCADE to all foreign keys that reference the scans table.
-- V7 added cascade from projects → scans but the child tables of scans were missed,
-- causing FK violations when a scan row is deleted.

ALTER TABLE scan_dependencies
    DROP CONSTRAINT fk_sd_scan,
    ADD CONSTRAINT fk_sd_scan
        FOREIGN KEY (scan_id) REFERENCES scans (id) ON DELETE CASCADE;

ALTER TABLE eol_records
    DROP CONSTRAINT fk_eol_scan,
    ADD CONSTRAINT fk_eol_scan
        FOREIGN KEY (scan_id) REFERENCES scans (id) ON DELETE CASCADE;

ALTER TABLE dependency_vulnerabilities
    DROP CONSTRAINT fk_depvuln_scan,
    ADD CONSTRAINT fk_depvuln_scan
        FOREIGN KEY (scan_id) REFERENCES scans (id) ON DELETE CASCADE;

ALTER TABLE risk_assessments
    DROP CONSTRAINT fk_risk_scan,
    ADD CONSTRAINT fk_risk_scan
        FOREIGN KEY (scan_id) REFERENCES scans (id) ON DELETE CASCADE;

ALTER TABLE remediation_recommendations
    DROP CONSTRAINT fk_remediation_scan,
    ADD CONSTRAINT fk_remediation_scan
        FOREIGN KEY (scan_id) REFERENCES scans (id) ON DELETE CASCADE;
