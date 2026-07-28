ALTER TABLE agent_service.agent_jobs
    DROP CONSTRAINT ck_agent_job_status,
    DROP CONSTRAINT ck_agent_job_phase;

ALTER TABLE agent_service.agent_jobs
    ADD COLUMN planner_status VARCHAR(40),
    ADD CONSTRAINT ck_agent_job_status CHECK (
        status IN (
            'QUEUED', 'RUNNING', 'READY_FOR_ANALYSIS',
            'COMPLETED', 'PARTIALLY_COMPLETED', 'FAILED', 'CANCELLED'
        )
    ),
    ADD CONSTRAINT ck_agent_job_phase CHECK (
        phase IS NULL OR phase IN (
            'LOADING_CONTEXT', 'MODEL_RUNNING', 'VALIDATING', 'REPAIRING',
            'SUBMITTING_REVIEW', 'DISCOVERING_FILES', 'CLASSIFYING_FILES',
            'LOADING_CODE_METADATA', 'LOADING_BINDINGS',
            'BUILDING_SEMANTIC_GRAPH', 'BUILDING_ANALYSIS_UNITS',
            'READY_FOR_ANALYSIS', 'PLANNING_UNITS',
            'VALIDATING_UNIT_PLAN', 'EXECUTING_UNITS', 'COMPLETED'
        )
    ),
    ADD CONSTRAINT ck_agent_job_planner_status CHECK (
        planner_status IS NULL OR planner_status IN (
            'PENDING', 'RUNNING', 'VALIDATING', 'REPAIRING',
            'COMPLETED', 'FAILED'
        )
    );

ALTER TABLE agent_service.agent_units
    DROP CONSTRAINT ck_agent_unit_phase;

ALTER TABLE agent_service.agent_units
    ADD COLUMN summary VARCHAR(2000),
    ADD CONSTRAINT ck_agent_unit_phase CHECK (
        phase IS NULL OR phase IN (
            'LOADING_CONTEXT', 'MODEL_RUNNING', 'VALIDATING', 'REPAIRING',
            'SUBMITTING_REVIEW', 'DISCOVERING_FILES', 'CLASSIFYING_FILES',
            'LOADING_CODE_METADATA', 'LOADING_BINDINGS',
            'BUILDING_SEMANTIC_GRAPH', 'BUILDING_ANALYSIS_UNITS',
            'READY_FOR_ANALYSIS', 'PLANNING_UNITS',
            'VALIDATING_UNIT_PLAN', 'EXECUTING_UNITS', 'COMPLETED'
        )
    );

ALTER TABLE agent_service.agent_job_files
    ADD COLUMN route_hints JSONB NOT NULL DEFAULT '[]'::jsonb;
