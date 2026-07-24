package com.sugowslt.paymentcoreapi.gateway

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "payment.gateway.toss")
data class TossPaymentGatewayProperties(
    val baseUrl: String = "https://api.tosspayments.com",
    val secretKey: String = "",
    val connectTimeoutMs: Int = 1_000,
    val readTimeoutMs: Int = 3_000,
    val maxRetries: Int = 1,
)
