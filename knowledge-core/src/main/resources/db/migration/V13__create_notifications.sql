CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    recipient_user_id UUID NOT NULL REFERENCES user_accounts(id)
        ON DELETE CASCADE,
    workspace_id UUID NOT NULL REFERENCES workspaces(id)
        ON DELETE CASCADE,
    document_id UUID REFERENCES documents(id)
        ON DELETE CASCADE,
    type VARCHAR(60) NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT,
    source_event_id UUID NOT NULL,
    read_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE(recipient_user_id, source_event_id)
);

CREATE INDEX idx_notifications_recipient_created_at
    ON notifications(recipient_user_id, created_at DESC);

CREATE INDEX idx_notifications_recipient_unread
    ON notifications(recipient_user_id, read_at, created_at DESC);
