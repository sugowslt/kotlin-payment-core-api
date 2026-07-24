package com.sugowslt.paymentcoreapi.controller

import com.sugowslt.paymentcoreapi.controller.dto.PaymentOutboxMetricsResponse
import com.sugowslt.paymentcoreapi.controller.dto.PaymentOutboxRetryResponse
import com.sugowslt.paymentcoreapi.exception.WebhookReplayAccessDeniedException
import com.sugowslt.paymentcoreapi.gateway.WebhookOperationsProperties
import com.sugowslt.paymentcoreapi.service.PaymentOutboxService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

@RestController
@RequestMapping("/api/v1/internal/outbox")
class PaymentOutboxOperationsController(
    private val paymentOutboxService: PaymentOutboxService,
    private val webhookOperationsProperties: WebhookOperationsProperties,
) {

    @GetMapping("/metrics")
    fun metrics(
        @RequestHeader(name = "X-Webhook-Replay-Token", required = false) replayToken: String?,
    ): ResponseEntity<PaymentOutboxMetricsResponse> {
        authorize(replayToken)
        return ResponseEntity.ok(paymentOutboxService.metrics())
    }

    @PostMapping("/publish")
    fun publish(
        @RequestHeader(name = "X-Webhook-Replay-Token", required = false) replayToken: String?,
    ): ResponseEntity<PaymentOutboxMetricsResponse> {
        authorize(replayToken)
        return ResponseEntity.ok(paymentOutboxService.publishPending())
    }

    @PostMapping("/{eventId}/retry")
    fun retryFailed(
        @PathVariable eventId: Long,
        @RequestHeader(name = "X-Webhook-Replay-Token", required = false) replayToken: String?,
    ): ResponseEntity<PaymentOutboxRetryResponse> {
        authorize(replayToken)
        return ResponseEntity.ok(paymentOutboxService.retryFailed(eventId))
    }

    private fun authorize(requestToken: String?) {
        val configuredToken = webhookOperationsProperties.replayToken
        if (configuredToken.isBlank() || requestToken.isNullOrBlank()) {
            throw WebhookReplayAccessDeniedException()
        }

        if (!MessageDigest.isEqual(
                configuredToken.toByteArray(StandardCharsets.UTF_8),
                requestToken.toByteArray(StandardCharsets.UTF_8),
            )
        ) {
            throw WebhookReplayAccessDeniedException()
        }
    }
}
