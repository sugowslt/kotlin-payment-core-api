package com.sugowslt.paymentcoreapi.controller.dto

import com.sugowslt.paymentcoreapi.entity.PaymentMethod
import com.sugowslt.paymentcoreapi.entity.PaymentStatus
import java.math.BigDecimal
import java.time.LocalDateTime

data class PaymentSummary(
    val id: Long,
    val orderId: Long,
    val amount: BigDecimal,
    val method: PaymentMethod,
    val status: PaymentStatus,
    val createdAt: LocalDateTime,
)

data class GetPaymentsResponse(
    val content: List<PaymentSummary>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
)

data class GetPaymentsCursorResponse(
    val content: List<PaymentSummary>,
    val size: Int,
    val hasNext: Boolean,
    val nextCursorId: Long?,
)
