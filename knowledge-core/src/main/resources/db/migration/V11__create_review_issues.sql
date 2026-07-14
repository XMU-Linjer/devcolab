CREATE TABLE review_issues (
    id UUID PRIMARY KEY,
    document_version_id UUID NOT NULL REFERENCES document_versions(id)
        ON DELETE CASCADE,
    type VARCHAR(40) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL,
    assignee_id UUID REFERENCES user_accounts(id),
    title VARCHAR(200) NOT NULL,
    description TEXT,
    created_by UUID NOT NULL REFERENCES user_accounts(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_review_issues_document_version_id
    ON review_issues(document_version_id, created_at DESC);

CREATE INDEX idx_review_issues_status
    ON review_issues(status);
