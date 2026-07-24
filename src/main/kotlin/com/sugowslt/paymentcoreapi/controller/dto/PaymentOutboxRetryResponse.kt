package com.sugowslt.paymentcoreapi.controller.dto

import com.sugowslt.paymentcoreapi.entity.OutboxStatus
import java.time.LocalDateTime

data class PaymentOutboxRetryResponse(
    val eventId: Long,
    val status: OutboxStatus,
    val retryCount: Int,
    val nextAttemptAt: LocalDateTime?,
)
