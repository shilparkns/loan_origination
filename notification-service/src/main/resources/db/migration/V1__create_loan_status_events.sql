CREATE TABLE loan_status_events (
    id BIGSERIAL PRIMARY KEY,
    loan_id BIGINT NOT NULL,
    from_status VARCHAR(50) NOT NULL,
    to_status VARCHAR(50) NOT NULL,
    changed_by VARCHAR(255) NOT NULL,
    received_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_loan_status_events_loan_id ON loan_status_events(loan_id);
CREATE INDEX idx_loan_status_events_received_at ON loan_status_events(received_at);
