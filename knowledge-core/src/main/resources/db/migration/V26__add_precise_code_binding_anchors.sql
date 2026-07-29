ALTER TABLE code_document_bindings
    ADD COLUMN revision VARCHAR(255);
ALTER TABLE code_document_bindings
    ADD COLUMN anchor_kind VARCHAR(20) NOT NULL DEFAULT 'FILE';
ALTER TABLE code_document_bindings
    ADD COLUMN symbol_key VARCHAR(1000);
ALTER TABLE code_document_bindings
    ADD COLUMN start_line INTEGER;
ALTER TABLE code_document_bindings
    ADD COLUMN end_line INTEGER;
ALTER TABLE code_document_bindings
    ADD COLUMN revision_key VARCHAR(255) NOT NULL DEFAULT '';
ALTER TABLE code_document_bindings
    ADD COLUMN symbol_key_identity VARCHAR(1000) NOT NULL DEFAULT '';
ALTER TABLE code_document_bindings
    ADD COLUMN start_line_key INTEGER NOT NULL DEFAULT 0;
ALTER TABLE code_document_bindings
    ADD COLUMN end_line_key INTEGER NOT NULL DEFAULT 0;

ALTER TABLE code_document_bindings
    ADD CONSTRAINT ck_code_binding_anchor_kind
        CHECK (anchor_kind IN ('FILE', 'RANGE', 'SYMBOL'));
ALTER TABLE code_document_bindings
    ADD CONSTRAINT ck_code_binding_revision_not_blank
        CHECK (revision IS NULL OR TRIM(revision) <> '');
ALTER TABLE code_document_bindings
    ADD CONSTRAINT ck_code_binding_symbol_not_blank
        CHECK (symbol_key IS NULL OR TRIM(symbol_key) <> '');
ALTER TABLE code_document_bindings
    ADD CONSTRAINT ck_code_binding_range_pair
        CHECK ((start_line IS NULL AND end_line IS NULL)
            OR (start_line IS NOT NULL AND end_line IS NOT NULL));
ALTER TABLE code_document_bindings
    ADD CONSTRAINT ck_code_binding_range_order
        CHECK (start_line IS NULL OR (start_line >= 1 AND end_line >= start_line));
ALTER TABLE code_document_bindings
    ADD CONSTRAINT ck_code_binding_file_anchor
        CHECK (anchor_kind <> 'FILE' OR (
            symbol_key IS NULL AND start_line IS NULL AND end_line IS NULL
        ));
ALTER TABLE code_document_bindings
    ADD CONSTRAINT ck_code_binding_range_anchor
        CHECK (anchor_kind <> 'RANGE' OR (
            revision IS NOT NULL AND symbol_key IS NULL
            AND start_line IS NOT NULL AND end_line IS NOT NULL
        ));
ALTER TABLE code_document_bindings
    ADD CONSTRAINT ck_code_binding_symbol_anchor
        CHECK (anchor_kind <> 'SYMBOL'
            OR (revision IS NOT NULL AND symbol_key IS NOT NULL));
ALTER TABLE code_document_bindings
    ADD CONSTRAINT ck_code_binding_revision_key
        CHECK (revision_key = COALESCE(revision, ''));
ALTER TABLE code_document_bindings
    ADD CONSTRAINT ck_code_binding_symbol_key_identity
        CHECK (symbol_key_identity = COALESCE(symbol_key, ''));
ALTER TABLE code_document_bindings
    ADD CONSTRAINT ck_code_binding_start_line_key
        CHECK (start_line_key = COALESCE(start_line, 0));
ALTER TABLE code_document_bindings
    ADD CONSTRAINT ck_code_binding_end_line_key
        CHECK (end_line_key = COALESCE(end_line, 0));

ALTER TABLE code_document_bindings
    DROP CONSTRAINT uk_code_document_binding;

CREATE UNIQUE INDEX uk_code_document_binding_anchor
    ON code_document_bindings (
        repository_id,
        document_id,
        target_key,
        path_pattern,
        revision_key,
        anchor_kind,
        symbol_key_identity,
        start_line_key,
        end_line_key
    );

CREATE INDEX idx_code_binding_repository_revision
    ON code_document_bindings(repository_id, revision);

CREATE INDEX idx_code_binding_repository_path
    ON code_document_bindings(repository_id, path_pattern);

CREATE INDEX idx_code_binding_document_revision
    ON code_document_bindings(document_id, revision);

CREATE INDEX idx_code_binding_block
    ON code_document_bindings(block_id);
