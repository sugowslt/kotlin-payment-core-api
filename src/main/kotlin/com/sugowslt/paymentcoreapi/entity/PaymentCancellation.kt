package com.sugowslt.paymentcoreapi.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(
    name = "payment_cancellations",
    indexes = [Index(name = "idx_payment_cancellation_payment", columnList = "payment_id,created_at")],
)
class PaymentCancellation(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "payment_id", nullable = false)
    val paymentId: Long,

    @Column(name = "cancellation_idempotency_key", nullable = false, unique = true, length = 120)
    val cancellationIdempotencyKey: String,

    @Column(name = "cancel_amount", nullable = false, precision = 18, scale = 2)
    val cancelAmount: BigDecimal,

    @Column(name = "cancel_reason", nullable = false, length = 200)
    val cancelReason: String,

    @Column(name = "provider_cancellation_id", length = 120)
    val providerCancellationId: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
)
