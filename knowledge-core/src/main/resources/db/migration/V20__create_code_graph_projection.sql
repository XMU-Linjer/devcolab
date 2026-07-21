CREATE TABLE code_symbols (
    id UUID PRIMARY KEY,
    repository_id UUID NOT NULL REFERENCES git_repositories(id) ON DELETE CASCADE,
    file_path VARCHAR(1000) NOT NULL,
    symbol_key VARCHAR(1500) NOT NULL,
    language VARCHAR(40) NOT NULL,
    symbol_kind VARCHAR(40) NOT NULL,
    qualified_name VARCHAR(1000) NOT NULL,
    simple_name VARCHAR(300) NOT NULL,
    signature VARCHAR(1500),
    parent_symbol_key VARCHAR(1500),
    start_line INTEGER,
    end_line INTEGER,
    CONSTRAINT uk_code_symbol_key UNIQUE (repository_id, symbol_key)
);

CREATE INDEX idx_code_symbol_repository_file
    ON code_symbols(repository_id, file_path);

CREATE TABLE code_symbol_dependencies (
    id UUID PRIMARY KEY,
    repository_id UUID NOT NULL REFERENCES git_repositories(id) ON DELETE CASCADE,
    source_symbol_key VARCHAR(1500) NOT NULL,
    target_symbol_key VARCHAR(1500) NOT NULL,
    relation_type VARCHAR(40) NOT NULL,
    evidence_file_path VARCHAR(1000) NOT NULL,
    CONSTRAINT uk_code_symbol_dependency UNIQUE (
        repository_id, source_symbol_key, target_symbol_key, relation_type
    )
);

CREATE INDEX idx_code_symbol_dependency_source
    ON code_symbol_dependencies(repository_id, source_symbol_key);

CREATE TABLE code_file_dependencies (
    id UUID PRIMARY KEY,
    repository_id UUID NOT NULL REFERENCES git_repositories(id) ON DELETE CASCADE,
    source_path VARCHAR(1000) NOT NULL,
    target_path VARCHAR(1000) NOT NULL,
    relation_type VARCHAR(40) NOT NULL,
    CONSTRAINT uk_code_file_dependency UNIQUE (
        repository_id, source_path, target_path, relation_type
    )
);

CREATE INDEX idx_code_file_dependency_source
    ON code_file_dependencies(repository_id, source_path);
