package com.sugowslt.paymentcoreapi.gateway

import com.sugowslt.paymentcoreapi.entity.PaymentMethod
import java.math.BigDecimal

interface PaymentGateway {
    fun approve(request: PaymentGatewayApprovalRequest): PaymentGatewayApprovalResult

    fun cancel(request: PaymentGatewayCancellationRequest): PaymentGatewayCancellationResult
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

data class PaymentGatewayCancellationRequest(
    val paymentId: Long,
    val providerTransactionId: String?,
    val cancelReason: String = "customer requested cancellation",
    val cancellationIdempotencyKey: String = "payment-cancel-$paymentId",
)

data class PaymentGatewayCancellationResult(
    val providerCancellationId: String? = null,
)
