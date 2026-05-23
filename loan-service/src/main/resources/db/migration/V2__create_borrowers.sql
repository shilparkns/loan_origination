CREATE TABLE borrowers (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT         NOT NULL REFERENCES users(id),
    first_name    VARCHAR(100)   NOT NULL,
    last_name     VARCHAR(100)   NOT NULL,
    email         VARCHAR(255)   NOT NULL,
    phone         VARCHAR(20),
    credit_score  INTEGER        NOT NULL CHECK (credit_score >= 300 AND credit_score <= 850),
    annual_income NUMERIC(15, 2) NOT NULL
);
