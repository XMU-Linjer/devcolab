ALTER TABLE git_changes ADD COLUMN author_email VARCHAR(320);
ALTER TABLE git_changes ADD COLUMN authored_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE git_changes ADD COLUMN committer_name VARCHAR(200);
ALTER TABLE git_changes ADD COLUMN committer_email VARCHAR(320);
ALTER TABLE git_changes ADD COLUMN parent_commit_sha VARCHAR(64);

ALTER TABLE git_file_diffs ADD COLUMN binary_file BOOLEAN NOT NULL DEFAULT FALSE;
