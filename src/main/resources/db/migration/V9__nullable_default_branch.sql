-- V9: Allow default_branch to be NULL so that the scan pipeline uses the remote's actual HEAD
-- instead of assuming 'main'. Existing rows keep their stored value unchanged.

ALTER TABLE projects
    ALTER COLUMN default_branch DROP NOT NULL,
    ALTER COLUMN default_branch DROP DEFAULT;
