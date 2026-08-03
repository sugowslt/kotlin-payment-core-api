package com.sugowslt.paymentcoreapi.controller

import com.sugowslt.paymentcoreapi.controller.dto.PaymentWebhookResponse
import com.sugowslt.paymentcoreapi.controller.dto.TossPaymentStatusWebhookRequest
import com.sugowslt.paymentcoreapi.service.PaymentWebhookService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/webhooks/toss")
@Tag(name = "Toss Webhooks", description = "Toss 결제 상태 웹훅 수신 API")
class PaymentWebhookController(
    private val paymentWebhookService: PaymentWebhookService,
) {

    @PostMapping("/payments")
    @Operation(summary = "Toss 결제 상태 웹훅 수신", description = "transmission ID 중복을 보정하며 결제 상태 변경을 반영합니다.")
    fun receivePaymentStatusChanged(
        @RequestHeader(name = "tosspayments-webhook-transmission-id", required = false)
        transmissionId: String?,
        @RequestBody request: TossPaymentStatusWebhookRequest,
    ): ResponseEntity<PaymentWebhookResponse> {
        val result = paymentWebhookService.handleTossPaymentStatusChanged(transmissionId.orEmpty(), request)
        return ResponseEntity.ok(result)
    }
}
