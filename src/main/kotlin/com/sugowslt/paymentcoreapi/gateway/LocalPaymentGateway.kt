package com.sugowslt.paymentcoreapi.gateway

import org.springframework.stereotype.Component
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty

@Component
@ConditionalOnProperty(name = ["payment.gateway.provider"], havingValue = "local", matchIfMissing = true)
class LocalPaymentGateway : PaymentGateway {
    override fun approve(request: PaymentGatewayApprovalRequest): PaymentGatewayApprovalResult {
        return PaymentGatewayApprovalResult(
            providerTransactionId = "local-${request.paymentId}",
        )
    }
}
