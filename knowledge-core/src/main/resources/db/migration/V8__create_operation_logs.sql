CREATE TABLE operation_logs (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    document_id UUID REFERENCES documents(id) ON DELETE CASCADE,
    action VARCHAR(60) NOT NULL,
    message VARCHAR(500) NOT NULL,
    operator_user_id UUID NOT NULL REFERENCES user_accounts(id),
    target_type VARCHAR(60) NOT NULL,
    target_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_operation_logs_document_id
    ON operation_logs(document_id, created_at DESC);

CREATE INDEX idx_operation_logs_workspace_id
    ON operation_logs(workspace_id, created_at DESC);
