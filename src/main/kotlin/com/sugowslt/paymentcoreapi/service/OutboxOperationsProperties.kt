package com.sugowslt.paymentcoreapi.service

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "payment.outbox")
data class OutboxOperationsProperties(
    val maxRetries: Int = 3,
    val retryDelaySeconds: Long = 30,
)
