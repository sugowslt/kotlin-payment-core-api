package com.sugowslt.paymentcoreapi.controller.dto

data class PaymentOutboxMetricsResponse(
    val pendingEvents: Long,
    val retryingEvents: Long,
    val publishedEvents: Long,
    val failedEvents: Long,
)
