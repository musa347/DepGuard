-- V7: Cascade project deletion to scans.
-- The original fk_scans_project constraint has no ON DELETE CASCADE, so deleting a project
-- with existing scans would fail with a FK violation. Re-create it with CASCADE.
ALTER TABLE scans DROP CONSTRAINT fk_scans_project;
ALTER TABLE scans ADD CONSTRAINT fk_scans_project
    FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE;
