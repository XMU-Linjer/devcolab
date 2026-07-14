ALTER TABLE document_versions
    ADD COLUMN status VARCHAR(30) NOT NULL DEFAULT 'CURRENT';

UPDATE document_versions
   SET status = 'SUPERSEDED'
 WHERE id NOT IN (
       SELECT latest.id
         FROM document_versions latest
         JOIN (
              SELECT document_id, MAX(version_no) AS version_no
                FROM document_versions
               GROUP BY document_id
         ) grouped
           ON latest.document_id = grouped.document_id
          AND latest.version_no = grouped.version_no
 );

CREATE INDEX idx_document_versions_status
    ON document_versions(status);
