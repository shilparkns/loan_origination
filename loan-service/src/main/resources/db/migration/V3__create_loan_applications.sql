CREATE TABLE loan_applications (
    id               BIGSERIAL      PRIMARY KEY,
    borrower_id      BIGINT         NOT NULL REFERENCES borrowers(id),
    created_by       BIGINT         NOT NULL REFERENCES users(id),
    loan_amount      NUMERIC(15, 2) NOT NULL CHECK (loan_amount > 0),
    property_address VARCHAR(500)   NOT NULL,
    status           VARCHAR(50)    NOT NULL,
    created_at       TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP      NOT NULL DEFAULT NOW()
);
