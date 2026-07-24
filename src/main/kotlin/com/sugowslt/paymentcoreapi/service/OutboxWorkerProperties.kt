package com.sugowslt.paymentcoreapi.service

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "payment.outbox.worker")
data class OutboxWorkerProperties(
    val enabled: Boolean = false,
    val fixedDelayMs: Long = 30_000,
)
