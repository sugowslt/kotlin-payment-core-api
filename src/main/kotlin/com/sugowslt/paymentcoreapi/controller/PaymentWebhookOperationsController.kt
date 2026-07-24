package com.sugowslt.paymentcoreapi.controller

import com.sugowslt.paymentcoreapi.controller.dto.PaymentWebhookMetricsResponse
import com.sugowslt.paymentcoreapi.controller.dto.PaymentWebhookResponse
import com.sugowslt.paymentcoreapi.exception.WebhookReplayAccessDeniedException
import com.sugowslt.paymentcoreapi.gateway.WebhookOperationsProperties
import com.sugowslt.paymentcoreapi.service.PaymentWebhookService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

@RestController
@RequestMapping("/api/v1/internal/webhooks")
class PaymentWebhookOperationsController(
    private val paymentWebhookService: PaymentWebhookService,
    private val webhookOperationsProperties: WebhookOperationsProperties,
) {

    @PostMapping("/{transmissionId}/replay")
    fun replay(
        @PathVariable transmissionId: String,
        @RequestHeader(name = "X-Webhook-Replay-Token", required = false) replayToken: String?,
    ): ResponseEntity<PaymentWebhookResponse> {
        authorize(replayToken)
        return ResponseEntity.ok(paymentWebhookService.replay(transmissionId))
    }

    @GetMapping("/metrics")
    fun metrics(
        @RequestHeader(name = "X-Webhook-Replay-Token", required = false) replayToken: String?,
    ): ResponseEntity<PaymentWebhookMetricsResponse> {
        authorize(replayToken)
        return ResponseEntity.ok(paymentWebhookService.metrics())
    }

    private fun authorize(requestToken: String?) {
        val configuredToken = webhookOperationsProperties.replayToken
        if (configuredToken.isBlank() || requestToken.isNullOrBlank()) {
            throw WebhookReplayAccessDeniedException()
        }

        val matches = MessageDigest.isEqual(
            configuredToken.toByteArray(StandardCharsets.UTF_8),
            requestToken.toByteArray(StandardCharsets.UTF_8),
        )
        if (!matches) {
            throw WebhookReplayAccessDeniedException()
        }
    }
}
