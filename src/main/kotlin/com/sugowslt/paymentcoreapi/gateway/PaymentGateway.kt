package com.sugowslt.paymentcoreapi.gateway

import com.sugowslt.paymentcoreapi.entity.PaymentMethod
import java.math.BigDecimal

interface PaymentGateway {
    fun approve(request: PaymentGatewayApprovalRequest): PaymentGatewayApprovalResult
}

data class PaymentGatewayApprovalRequest(
    val paymentId: Long,
    val orderId: Long,
    val amount: BigDecimal,
    val method: PaymentMethod,
    val approvalIdempotencyKey: String,
    val paymentKey: String? = null,
)

data class PaymentGatewayApprovalResult(
    val providerTransactionId: String,
)
