ALTER TABLE document_change_binding_proposals
    ADD COLUMN revision VARCHAR(255);
ALTER TABLE document_change_binding_proposals
    ADD COLUMN anchor_kind VARCHAR(20) NOT NULL DEFAULT 'FILE';
ALTER TABLE document_change_binding_proposals
    ADD COLUMN symbol_key VARCHAR(1000);
ALTER TABLE document_change_binding_proposals
    ADD COLUMN start_line INTEGER;
ALTER TABLE document_change_binding_proposals
    ADD COLUMN end_line INTEGER;
ALTER TABLE document_change_binding_proposals
    ADD COLUMN block_id UUID REFERENCES document_blocks(id);
ALTER TABLE document_change_binding_proposals
    ADD COLUMN created_block_operation_id UUID;
ALTER TABLE document_change_binding_proposals
    ADD COLUMN candidate_id VARCHAR(100);
ALTER TABLE document_change_binding_proposals
    ADD COLUMN document_anchor_candidate_id VARCHAR(100);
ALTER TABLE document_change_binding_proposals
    ADD COLUMN confidence NUMERIC(5, 4);

ALTER TABLE document_change_binding_proposals
    ADD CONSTRAINT fk_document_change_binding_proposal_created_block
        FOREIGN KEY (created_block_operation_id)
        REFERENCES document_change_operations(id);
ALTER TABLE document_change_binding_proposals
    ADD CONSTRAINT ck_document_change_binding_proposal_document_target
        CHECK ((document_id IS NULL) <> (created_document_operation_id IS NULL));
ALTER TABLE document_change_binding_proposals
    ADD CONSTRAINT ck_document_change_binding_proposal_block_target
        CHECK (block_id IS NULL OR created_block_operation_id IS NULL);
ALTER TABLE document_change_binding_proposals
    ADD CONSTRAINT ck_document_change_binding_proposal_anchor_kind
        CHECK (anchor_kind IN ('FILE', 'RANGE', 'SYMBOL'));
ALTER TABLE document_change_binding_proposals
    ADD CONSTRAINT ck_document_change_binding_proposal_revision_not_blank
        CHECK (revision IS NULL OR TRIM(revision) <> '');
ALTER TABLE document_change_binding_proposals
    ADD CONSTRAINT ck_document_change_binding_proposal_symbol_not_blank
        CHECK (symbol_key IS NULL OR TRIM(symbol_key) <> '');
ALTER TABLE document_change_binding_proposals
    ADD CONSTRAINT ck_document_change_binding_proposal_candidate_not_blank
        CHECK (candidate_id IS NULL OR TRIM(candidate_id) <> '');
ALTER TABLE document_change_binding_proposals
    ADD CONSTRAINT ck_document_change_binding_proposal_doc_candidate_not_blank
        CHECK (
            document_anchor_candidate_id IS NULL
            OR TRIM(document_anchor_candidate_id) <> ''
        );
ALTER TABLE document_change_binding_proposals
    ADD CONSTRAINT ck_document_change_binding_proposal_range_pair
        CHECK (
            (start_line IS NULL AND end_line IS NULL)
            OR (start_line IS NOT NULL AND end_line IS NOT NULL)
        );
ALTER TABLE document_change_binding_proposals
    ADD CONSTRAINT ck_document_change_binding_proposal_range_order
        CHECK (start_line IS NULL OR (start_line >= 1 AND end_line >= start_line));
ALTER TABLE document_change_binding_proposals
    ADD CONSTRAINT ck_document_change_binding_proposal_file_anchor
        CHECK (
            anchor_kind <> 'FILE'
            OR (symbol_key IS NULL AND start_line IS NULL AND end_line IS NULL)
        );
ALTER TABLE document_change_binding_proposals
    ADD CONSTRAINT ck_document_change_binding_proposal_range_anchor
        CHECK (
            anchor_kind <> 'RANGE'
            OR (
                revision IS NOT NULL
                AND symbol_key IS NULL
                AND start_line IS NOT NULL
                AND end_line IS NOT NULL
            )
        );
ALTER TABLE document_change_binding_proposals
    ADD CONSTRAINT ck_document_change_binding_proposal_symbol_anchor
        CHECK (
            anchor_kind <> 'SYMBOL'
            OR (revision IS NOT NULL AND symbol_key IS NOT NULL)
        );
ALTER TABLE document_change_binding_proposals
    ADD CONSTRAINT ck_document_change_binding_proposal_confidence
        CHECK (confidence IS NULL OR (confidence >= 0 AND confidence <= 1));

CREATE INDEX idx_document_change_binding_proposal_block
    ON document_change_binding_proposals(block_id);
