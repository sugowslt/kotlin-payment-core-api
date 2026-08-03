package com.sugowslt.paymentcoreapi.settlement

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "payment.settlement")
data class SettlementProperties(
    val feeRateBps: Int = 300,
)
