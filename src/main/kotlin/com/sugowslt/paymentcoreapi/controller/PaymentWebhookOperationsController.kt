package com.sugowslt.paymentcoreapi.controller

import com.sugowslt.paymentcoreapi.controller.dto.PaymentWebhookMetricsResponse
import com.sugowslt.paymentcoreapi.controller.dto.PaymentWebhookResponse
import com.sugowslt.paymentcoreapi.entity.InternalOperationAuditOutcome
import com.sugowslt.paymentcoreapi.filter.TraceIdFilter
import com.sugowslt.paymentcoreapi.security.InternalOperationsAuthorizer
import com.sugowslt.paymentcoreapi.service.InternalOperationAuditService
import com.sugowslt.paymentcoreapi.service.PaymentWebhookService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/internal/webhooks")
class PaymentWebhookOperationsController(
    private val paymentWebhookService: PaymentWebhookService,
    private val operationsAuthorizer: InternalOperationsAuthorizer,
    private val auditService: InternalOperationAuditService,
) {

    @PostMapping("/{transmissionId}/replay")
    fun replay(
        @PathVariable transmissionId: String,
        @RequestHeader(name = InternalOperationsAuthorizer.TOKEN_HEADER, required = false) replayToken: String?,
        request: HttpServletRequest,
    ): ResponseEntity<PaymentWebhookResponse> {
        operationsAuthorizer.authorize(replayToken)
        return try {
            val response = paymentWebhookService.replay(transmissionId)
            auditService.record(
                "WEBHOOK_REPLAY",
                transmissionId,
                InternalOperationAuditOutcome.SUCCESS,
                traceIdOf(request),
            )
            ResponseEntity.ok(response)
        } catch (ex: RuntimeException) {
            auditService.record(
                "WEBHOOK_REPLAY",
                transmissionId,
                InternalOperationAuditOutcome.FAILED,
                traceIdOf(request),
                ex.message,
            )
            throw ex
        }
    }

    @GetMapping("/metrics")
    fun metrics(
        @RequestHeader(name = InternalOperationsAuthorizer.TOKEN_HEADER, required = false) replayToken: String?,
    ): ResponseEntity<PaymentWebhookMetricsResponse> {
        operationsAuthorizer.authorize(replayToken)
        return ResponseEntity.ok(paymentWebhookService.metrics())
    }

    private fun traceIdOf(request: HttpServletRequest): String? {
        return request.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE)?.toString()
    }
}
