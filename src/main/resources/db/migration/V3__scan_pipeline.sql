-- V3: scan pipeline indexes
-- The scans, dependencies and scan_dependencies tables themselves were created in V1__init.sql, which
-- already contains the scan reproducibility columns (commit_sha, branch, started_at, completed_at) that
-- the implementation plan's Task 4 lists — so this migration only adds the index supporting status
-- inspection of the async pipeline.

CREATE INDEX idx_scans_status ON scans (status);
