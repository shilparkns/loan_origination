CREATE TABLE loan_documents (
    id                  BIGSERIAL    PRIMARY KEY,
    loan_application_id BIGINT       NOT NULL REFERENCES loan_applications(id),
    uploaded_by         BIGINT       NOT NULL REFERENCES users(id),
    document_type       VARCHAR(100) NOT NULL,
    file_path           VARCHAR(500) NOT NULL,
    uploaded_at         TIMESTAMP    NOT NULL DEFAULT NOW()
);
