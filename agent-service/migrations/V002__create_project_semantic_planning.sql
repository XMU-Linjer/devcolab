ALTER TABLE agent_service.agent_jobs
    DROP CONSTRAINT ck_agent_job_scope,
    DROP CONSTRAINT ck_agent_job_status;

ALTER TABLE agent_service.agent_jobs
    ADD COLUMN phase VARCHAR(50),
    ADD COLUMN discovered_file_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN supported_code_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN skipped_file_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN skipped_reason_counts JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN metadata_parsed_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN metadata_failed_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN bound_file_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN unbound_file_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN analysis_unit_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN overlapping_file_count INTEGER NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_agent_job_scope CHECK (
        scope_type IN ('CURRENT_FILE', 'PROJECT_INITIALIZATION')
    ),
    ADD CONSTRAINT ck_agent_job_status CHECK (
        status IN (
            'QUEUED', 'RUNNING', 'READY_FOR_ANALYSIS',
            'COMPLETED', 'FAILED', 'CANCELLED'
        )
    ),
    ADD CONSTRAINT ck_agent_job_phase CHECK (
        phase IS NULL OR phase IN (
            'LOADING_CONTEXT', 'MODEL_RUNNING', 'VALIDATING', 'REPAIRING',
            'SUBMITTING_REVIEW', 'DISCOVERING_FILES', 'CLASSIFYING_FILES',
            'LOADING_CODE_METADATA', 'LOADING_BINDINGS',
            'BUILDING_SEMANTIC_GRAPH', 'BUILDING_ANALYSIS_UNITS',
            'READY_FOR_ANALYSIS'
        )
    );

ALTER TABLE agent_service.agent_units
    DROP CONSTRAINT ck_agent_unit_status,
    DROP CONSTRAINT ck_agent_unit_phase;

ALTER TABLE agent_service.agent_units
    ADD COLUMN unit_kind VARCHAR(40) NOT NULL DEFAULT 'CURRENT_FILE_ANALYSIS',
    ADD COLUMN semantic_key VARCHAR(300),
    ADD COLUMN display_name VARCHAR(300),
    ADD COLUMN semantic_kind VARCHAR(50),
    ADD COLUMN primary_directory VARCHAR(2048),
    ADD COLUMN language_set JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN estimated_size_bytes BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN grouping_reasons JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN unit_fingerprint VARCHAR(64),
    ADD CONSTRAINT ck_agent_unit_kind CHECK (
        unit_kind IN (
            'CURRENT_FILE_ANALYSIS', 'PROJECT_DISCOVERY', 'SEMANTIC_ANALYSIS'
        )
    ),
    ADD CONSTRAINT ck_agent_unit_status CHECK (
        status IN (
            'PENDING', 'CLAIMED', 'RUNNING', 'RETRY_WAITING',
            'READY_FOR_ANALYSIS', 'COMPLETED', 'FAILED', 'CANCELLED'
        )
    ),
    ADD CONSTRAINT ck_agent_unit_phase CHECK (
        phase IS NULL OR phase IN (
            'LOADING_CONTEXT', 'MODEL_RUNNING', 'VALIDATING', 'REPAIRING',
            'SUBMITTING_REVIEW', 'DISCOVERING_FILES', 'CLASSIFYING_FILES',
            'LOADING_CODE_METADATA', 'LOADING_BINDINGS',
            'BUILDING_SEMANTIC_GRAPH', 'BUILDING_ANALYSIS_UNITS',
            'READY_FOR_ANALYSIS'
        )
    ),
    ADD CONSTRAINT uk_agent_unit_job_fingerprint UNIQUE (job_id, unit_fingerprint);

CREATE TABLE agent_service.agent_job_files (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL REFERENCES agent_service.agent_jobs(id) ON DELETE CASCADE,
    repository_id UUID NOT NULL,
    revision VARCHAR(64) NOT NULL,
    file_path VARCHAR(2048) NOT NULL,
    file_name VARCHAR(512) NOT NULL,
    extension VARCHAR(64) NOT NULL,
    language VARCHAR(100),
    size_bytes BIGINT NOT NULL,
    classification VARCHAR(50) NOT NULL,
    package_name VARCHAR(1000),
    module_key VARCHAR(1000),
    layer_hint VARCHAR(100),
    role_hints JSONB NOT NULL DEFAULT '[]'::jsonb,
    import_keys JSONB NOT NULL DEFAULT '[]'::jsonb,
    exported_symbols JSONB NOT NULL DEFAULT '[]'::jsonb,
    top_level_symbols JSONB NOT NULL DEFAULT '[]'::jsonb,
    is_generated BOOLEAN NOT NULL DEFAULT FALSE,
    metadata_error VARCHAR(300),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_agent_job_file_path UNIQUE (job_id, file_path),
    CONSTRAINT ck_agent_job_file_classification CHECK (
        classification IN (
            'SUPPORTED_CODE', 'TEXT_NON_CODE_SKIPPED', 'BINARY_SKIPPED',
            'GENERATED_SKIPPED', 'VENDOR_SKIPPED', 'OVERSIZED_SKIPPED',
            'UNSUPPORTED_EXTENSION_SKIPPED'
        )
    )
);

CREATE TABLE agent_service.agent_unit_files (
    unit_id UUID NOT NULL REFERENCES agent_service.agent_units(id) ON DELETE CASCADE,
    job_file_id UUID NOT NULL REFERENCES agent_service.agent_job_files(id) ON DELETE CASCADE,
    file_path VARCHAR(2048) NOT NULL,
    role VARCHAR(40) NOT NULL,
    relevance_reason VARCHAR(300) NOT NULL,
    ordinal INTEGER NOT NULL,
    PRIMARY KEY (unit_id, job_file_id, role),
    CONSTRAINT ck_agent_unit_file_role CHECK (
        role IN (
            'PRIMARY', 'SUPPORTING', 'BOUND_CONTEXT',
            'API_CONTRACT', 'SECURITY_RELATED', 'DEPENDENCY'
        )
    )
);

CREATE TABLE agent_service.agent_unit_documents (
    unit_id UUID NOT NULL REFERENCES agent_service.agent_units(id) ON DELETE CASCADE,
    document_id UUID NOT NULL,
    relationship VARCHAR(40) NOT NULL,
    source VARCHAR(100) NOT NULL,
    ordinal INTEGER NOT NULL,
    PRIMARY KEY (unit_id, document_id, relationship),
    CONSTRAINT ck_agent_unit_document_relationship CHECK (
        relationship IN ('BOUND', 'RELATED_BOUND', 'CANDIDATE_TARGET')
    )
);

CREATE INDEX ix_agent_job_files_job_classification
    ON agent_service.agent_job_files(job_id, classification, file_path);

CREATE INDEX ix_agent_unit_files_job_file
    ON agent_service.agent_unit_files(job_file_id, unit_id);

CREATE INDEX ix_agent_unit_documents_document
    ON agent_service.agent_unit_documents(document_id, unit_id);
