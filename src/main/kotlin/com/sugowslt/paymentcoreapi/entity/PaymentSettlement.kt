package com.sugowslt.paymentcoreapi.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "payment_settlements")
class PaymentSettlement(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "payment_id", nullable = false, unique = true)
    val paymentId: Long,

    @Column(name = "gross_amount", nullable = false, precision = 18, scale = 2)
    val grossAmount: BigDecimal,

    @Column(name = "fee_rate_bps", nullable = false)
    val feeRateBps: Int,

    @Column(name = "fee_amount", nullable = false, precision = 18, scale = 2)
    val feeAmount: BigDecimal,

    @Column(name = "settlement_amount", nullable = false, precision = 18, scale = 2)
    val settlementAmount: BigDecimal,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: SettlementStatus = SettlementStatus.REQUESTED,

    @Column(name = "failure_reason", length = 500)
    var failureReason: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),
)
