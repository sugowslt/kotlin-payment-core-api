package com.sugowslt.paymentcoreapi.controller

import com.sugowslt.paymentcoreapi.controller.dto.PaymentOutboxMetricsResponse
import com.sugowslt.paymentcoreapi.controller.dto.PaymentOutboxRetryResponse
import com.sugowslt.paymentcoreapi.entity.InternalOperationAuditOutcome
import com.sugowslt.paymentcoreapi.filter.TraceIdFilter
import com.sugowslt.paymentcoreapi.security.InternalOperationsAuthorizer
import com.sugowslt.paymentcoreapi.service.InternalOperationAuditService
import com.sugowslt.paymentcoreapi.service.PaymentOutboxService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/internal/outbox")
class PaymentOutboxOperationsController(
    private val paymentOutboxService: PaymentOutboxService,
    private val operationsAuthorizer: InternalOperationsAuthorizer,
    private val auditService: InternalOperationAuditService,
) {

    @GetMapping("/metrics")
    fun metrics(
        @RequestHeader(name = InternalOperationsAuthorizer.TOKEN_HEADER, required = false) replayToken: String?,
    ): ResponseEntity<PaymentOutboxMetricsResponse> {
        operationsAuthorizer.authorize(replayToken)
        return ResponseEntity.ok(paymentOutboxService.metrics())
    }

    @PostMapping("/publish")
    fun publish(
        @RequestHeader(name = InternalOperationsAuthorizer.TOKEN_HEADER, required = false) replayToken: String?,
        request: HttpServletRequest,
    ): ResponseEntity<PaymentOutboxMetricsResponse> {
        operationsAuthorizer.authorize(replayToken)
        return try {
            val response = paymentOutboxService.publishPending()
            auditService.record("OUTBOX_PUBLISH", null, InternalOperationAuditOutcome.SUCCESS, traceIdOf(request))
            ResponseEntity.ok(response)
        } catch (ex: RuntimeException) {
            auditService.record(
                "OUTBOX_PUBLISH",
                null,
                InternalOperationAuditOutcome.FAILED,
                traceIdOf(request),
                ex.message,
            )
            throw ex
        }
    }

    @PostMapping("/{eventId}/retry")
    fun retryFailed(
        @PathVariable eventId: Long,
        @RequestHeader(name = InternalOperationsAuthorizer.TOKEN_HEADER, required = false) replayToken: String?,
        request: HttpServletRequest,
    ): ResponseEntity<PaymentOutboxRetryResponse> {
        operationsAuthorizer.authorize(replayToken)
        return try {
            val response = paymentOutboxService.retryFailed(eventId)
            auditService.record(
                "OUTBOX_RETRY",
                eventId.toString(),
                InternalOperationAuditOutcome.SUCCESS,
                traceIdOf(request),
            )
            ResponseEntity.ok(response)
        } catch (ex: RuntimeException) {
            auditService.record(
                "OUTBOX_RETRY",
                eventId.toString(),
                InternalOperationAuditOutcome.FAILED,
                traceIdOf(request),
                ex.message,
            )
            throw ex
        }
    }

    private fun traceIdOf(request: HttpServletRequest): String? {
        return request.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE)?.toString()
    }
}
