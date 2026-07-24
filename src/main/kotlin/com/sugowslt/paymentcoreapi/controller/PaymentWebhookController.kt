package com.sugowslt.paymentcoreapi.controller

import com.sugowslt.paymentcoreapi.controller.dto.PaymentWebhookResponse
import com.sugowslt.paymentcoreapi.controller.dto.TossPaymentStatusWebhookRequest
import com.sugowslt.paymentcoreapi.service.PaymentWebhookService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/webhooks/toss")
class PaymentWebhookController(
    private val paymentWebhookService: PaymentWebhookService,
) {

    @PostMapping("/payments")
    fun receivePaymentStatusChanged(
        @RequestHeader(name = "tosspayments-webhook-transmission-id", required = false)
        transmissionId: String?,
        @RequestBody request: TossPaymentStatusWebhookRequest,
    ): ResponseEntity<PaymentWebhookResponse> {
        val result = paymentWebhookService.handleTossPaymentStatusChanged(transmissionId.orEmpty(), request)
        return ResponseEntity.ok(result)
    }
}
