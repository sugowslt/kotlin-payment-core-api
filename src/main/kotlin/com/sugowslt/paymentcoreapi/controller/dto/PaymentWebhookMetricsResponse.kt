package com.sugowslt.paymentcoreapi.controller.dto

data class PaymentWebhookMetricsResponse(
    val totalEvents: Long,
    val processedEvents: Long,
    val reprocessedEvents: Long,
    val ignoredEvents: Long,
)
