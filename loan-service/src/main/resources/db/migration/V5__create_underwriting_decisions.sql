CREATE TABLE underwriting_decisions (
    id                  BIGSERIAL    PRIMARY KEY,
    loan_application_id BIGINT       NOT NULL REFERENCES loan_applications(id),
    underwriter_id      BIGINT       NOT NULL REFERENCES users(id),
    decision            VARCHAR(20)  NOT NULL,
    notes               TEXT,
    decided_at          TIMESTAMP    NOT NULL DEFAULT NOW()
);
