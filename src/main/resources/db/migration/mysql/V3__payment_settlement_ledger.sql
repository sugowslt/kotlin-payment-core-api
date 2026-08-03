CREATE TABLE payment_settlements (
    id BIGINT AUTO_INCREMENT NOT NULL,
    payment_id BIGINT NOT NULL,
    gross_amount DECIMAL(18, 2) NOT NULL,
    fee_rate_bps INT NOT NULL,
    fee_amount DECIMAL(18, 2) NOT NULL,
    settlement_amount DECIMAL(18, 2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    failure_reason VARCHAR(500),
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_payment_settlements PRIMARY KEY (id),
    CONSTRAINT uk_payment_settlements_payment_id UNIQUE (payment_id)
);

CREATE INDEX idx_payment_settlements_status_created
    ON payment_settlements (status, created_at);
