CREATE TABLE notification_log (
    id BIGSERIAL PRIMARY KEY,
    loan_id BIGINT NOT NULL,
    notified_role VARCHAR(50) NOT NULL,
    message TEXT,
    notified_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_notification_log_loan_id ON notification_log(loan_id);
CREATE INDEX idx_notification_log_notified_role ON notification_log(notified_role);
CREATE INDEX idx_notification_log_notified_at ON notification_log(notified_at);
