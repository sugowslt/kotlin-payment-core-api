package com.sugowslt.paymentcoreapi.controller.dto

import com.sugowslt.paymentcoreapi.entity.PaymentMethod
import com.sugowslt.paymentcoreapi.entity.PaymentStatus
import java.math.BigDecimal
import java.time.LocalDateTime

data class CreatePaymentResponse(
    val id: Long,
    val orderId: Long,
    val idempotencyKey: String,
    val amount: BigDecimal,
    val method: PaymentMethod,
    val status: PaymentStatus,
    val createdAt: LocalDateTime,
)
