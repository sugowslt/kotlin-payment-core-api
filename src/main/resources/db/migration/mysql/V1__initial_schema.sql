CREATE TABLE payments (
    id BIGINT AUTO_INCREMENT NOT NULL,
    order_id BIGINT NOT NULL,
    idempotency_key VARCHAR(120) NOT NULL,
    approval_idempotency_key VARCHAR(120),
    cancellation_idempotency_key VARCHAR(120),
    provider_transaction_id VARCHAR(120),
    canceled_amount DECIMAL(18, 2) NOT NULL,
    amount DECIMAL(18, 2) NOT NULL,
    method VARCHAR(40) NOT NULL,
    status VARCHAR(40) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    deleted BOOLEAN NOT NULL,
    CONSTRAINT pk_payments PRIMARY KEY (id),
    CONSTRAINT uk_payments_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT uk_payments_approval_idempotency_key UNIQUE (approval_idempotency_key),
    CONSTRAINT uk_payments_cancellation_idempotency_key UNIQUE (cancellation_idempotency_key),
    CONSTRAINT uk_payments_provider_transaction_id UNIQUE (provider_transaction_id)
);

CREATE INDEX idx_payment_order_created ON payments (order_id, created_at);
CREATE INDEX idx_payment_status_created ON payments (status, created_at);

CREATE TABLE payment_cancellations (
    id BIGINT AUTO_INCREMENT NOT NULL,
    payment_id BIGINT NOT NULL,
    cancellation_idempotency_key VARCHAR(120) NOT NULL,
    cancel_amount DECIMAL(18, 2) NOT NULL,
    cancel_reason VARCHAR(200) NOT NULL,
    provider_cancellation_id VARCHAR(120),
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT pk_payment_cancellations PRIMARY KEY (id),
    CONSTRAINT uk_payment_cancellations_idempotency_key UNIQUE (cancellation_idempotency_key)
);

CREATE INDEX idx_payment_cancellation_payment ON payment_cancellations (payment_id, created_at);

CREATE TABLE payment_webhook_events (
    id BIGINT AUTO_INCREMENT NOT NULL,
    transmission_id VARCHAR(128) NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    order_id VARCHAR(64),
    provider_payment_key VARCHAR(200),
    provider_status VARCHAR(40),
    payload TEXT NOT NULL,
    outcome VARCHAR(60) NOT NULL,
    received_at TIMESTAMP NOT NULL,
    processed_at TIMESTAMP,
    CONSTRAINT pk_payment_webhook_events PRIMARY KEY (id),
    CONSTRAINT uk_payment_webhook_transmission_id UNIQUE (transmission_id)
);

CREATE TABLE payment_outbox_events (
    id BIGINT AUTO_INCREMENT NOT NULL,
    aggregate_type VARCHAR(40) NOT NULL,
    aggregate_id BIGINT NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    retry_count INT NOT NULL,
    last_error VARCHAR(500),
    next_attempt_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    published_at TIMESTAMP,
    CONSTRAINT pk_payment_outbox_events PRIMARY KEY (id)
);

CREATE TABLE internal_operation_audit_events (
    id BIGINT AUTO_INCREMENT NOT NULL,
    operation_name VARCHAR(80) NOT NULL,
    target_id VARCHAR(120),
    outcome VARCHAR(20) NOT NULL,
    trace_id VARCHAR(120),
    detail VARCHAR(500),
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT pk_internal_operation_audit_events PRIMARY KEY (id)
);

CREATE INDEX idx_internal_audit_created_at ON internal_operation_audit_events (created_at);
