CREATE TABLE audit_logs (
    id                  BIGSERIAL    PRIMARY KEY,
    loan_application_id BIGINT       NOT NULL REFERENCES loan_applications(id),
    changed_by          BIGINT       NOT NULL REFERENCES users(id),
    from_status         VARCHAR(50),
    to_status           VARCHAR(50)  NOT NULL,
    notes               TEXT,
    changed_at          TIMESTAMP    NOT NULL DEFAULT NOW()
);
