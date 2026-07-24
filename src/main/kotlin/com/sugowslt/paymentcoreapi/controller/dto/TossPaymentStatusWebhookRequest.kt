package com.sugowslt.paymentcoreapi.controller.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class TossPaymentStatusWebhookRequest(
    val eventType: String,
    val createdAt: String,
    val data: TossPaymentStatusWebhookData,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TossPaymentStatusWebhookData(
    val paymentKey: String? = null,
    val orderId: String? = null,
    val status: String,
)
