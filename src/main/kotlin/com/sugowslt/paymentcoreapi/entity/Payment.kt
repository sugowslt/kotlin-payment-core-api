package com.sugowslt.paymentcoreapi.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(
    name = "payments",
    indexes = [
        Index(name = "idx_payment_order_created", columnList = "order_id,created_at"),
        Index(name = "idx_payment_status_created", columnList = "status,created_at"),
    ],
)
class Payment(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "order_id", nullable = false)
    val orderId: Long,

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 120)
    val idempotencyKey: String,

    @Column(name = "approval_idempotency_key", unique = true, length = 120)
    var approvalIdempotencyKey: String? = null,

    @Column(name = "cancellation_idempotency_key", unique = true, length = 120)
    var cancellationIdempotencyKey: String? = null,

    @Column(name = "provider_transaction_id", unique = true, length = 120)
    var providerTransactionId: String? = null,

    @Column(name = "canceled_amount", nullable = false, precision = 18, scale = 2)
    var canceledAmount: BigDecimal = BigDecimal.ZERO,

    @Column(nullable = false, precision = 18, scale = 2)
    val amount: BigDecimal,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    val method: PaymentMethod,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    var status: PaymentStatus = PaymentStatus.PENDING,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "deleted", nullable = false)
    var deleted: Boolean = false,
) {
    fun approve(providerTransactionId: String) {
        status = PaymentStatus.APPROVED
        this.providerTransactionId = providerTransactionId
        updatedAt = LocalDateTime.now()
    }

    fun markFailed() {
        status = PaymentStatus.FAILED
        updatedAt = LocalDateTime.now()
    }

    fun applyCancellation(cancelAmount: BigDecimal, cancellationIdempotencyKey: String) {
        canceledAmount = canceledAmount.add(cancelAmount)
        if (canceledAmount.compareTo(amount) == 0) {
            status = PaymentStatus.CANCELED
        }
        this.cancellationIdempotencyKey = cancellationIdempotencyKey
        updatedAt = LocalDateTime.now()
    }

    fun markDeleted() {
        deleted = true
        updatedAt = LocalDateTime.now()
    }
}
