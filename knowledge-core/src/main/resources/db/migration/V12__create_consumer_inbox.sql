CREATE TABLE consumer_inbox (
    id UUID PRIMARY KEY,
    consumer_name VARCHAR(120) NOT NULL,
    event_id UUID NOT NULL,
    consumed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE (consumer_name, event_id)
);

CREATE INDEX idx_consumer_inbox_consumer_event
    ON consumer_inbox(consumer_name, event_id);