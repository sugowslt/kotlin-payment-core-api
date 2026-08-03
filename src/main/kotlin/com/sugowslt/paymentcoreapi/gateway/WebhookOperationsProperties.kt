package com.sugowslt.paymentcoreapi.gateway

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "payment.webhook")
data class WebhookOperationsProperties(
    val replayToken: String = "",
    val maxTokenLength: Int = 256,
)
