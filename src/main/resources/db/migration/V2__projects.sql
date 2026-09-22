-- V2: project registration constraints
-- The projects table itself is created in V1__init.sql. This migration adds the database-level
-- guarantee that a public GitHub repository is registered at most once, so a duplicate registration
-- is rejected by PostgreSQL even when two requests race past the application-level check.
-- The unique constraint also provides the lookup index for GET /api/projects deduplication checks.

ALTER TABLE projects
    ADD CONSTRAINT uq_projects_repository_url UNIQUE (repository_url);
