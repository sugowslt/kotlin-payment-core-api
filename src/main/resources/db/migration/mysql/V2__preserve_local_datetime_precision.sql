ALTER TABLE payments
    MODIFY COLUMN created_at TIMESTAMP(6) NOT NULL,
    MODIFY COLUMN updated_at TIMESTAMP(6) NOT NULL;

ALTER TABLE payment_cancellations
    MODIFY COLUMN created_at TIMESTAMP(6) NOT NULL;

ALTER TABLE payment_webhook_events
    MODIFY COLUMN received_at TIMESTAMP(6) NOT NULL,
    MODIFY COLUMN processed_at TIMESTAMP(6);

ALTER TABLE payment_outbox_events
    MODIFY COLUMN next_attempt_at TIMESTAMP(6),
    MODIFY COLUMN created_at TIMESTAMP(6) NOT NULL,
    MODIFY COLUMN published_at TIMESTAMP(6);

ALTER TABLE internal_operation_audit_events
    MODIFY COLUMN created_at TIMESTAMP(6) NOT NULL;
