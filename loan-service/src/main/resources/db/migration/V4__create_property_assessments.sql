CREATE TABLE property_assessments (
    id                  BIGSERIAL      PRIMARY KEY,
    loan_application_id BIGINT         NOT NULL REFERENCES loan_applications(id),
    appraiser_id        BIGINT         NOT NULL REFERENCES users(id),
    assessed_value      NUMERIC(15, 2) NOT NULL,
    assessed_at         TIMESTAMP      NOT NULL DEFAULT NOW()
);
