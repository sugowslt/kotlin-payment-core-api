package com.sugowslt.paymentcoreapi.controller

import com.sugowslt.paymentcoreapi.controller.dto.PaymentOutboxMetricsResponse
import com.sugowslt.paymentcoreapi.controller.dto.PaymentOutboxRetryResponse
import com.sugowslt.paymentcoreapi.entity.InternalOperationAuditOutcome
import com.sugowslt.paymentcoreapi.filter.TraceIdFilter
import com.sugowslt.paymentcoreapi.security.InternalOperationsAuthorizer
import com.sugowslt.paymentcoreapi.service.InternalOperationAuditService
import com.sugowslt.paymentcoreapi.service.PaymentOutboxService
import jakarta.servlet.http.HttpServletRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/internal/outbox")
@Tag(name = "Internal Outbox Operations", description = "보호된 아웃박스 발행·재시도·지표 API")
class PaymentOutboxOperationsController(
    private val paymentOutboxService: PaymentOutboxService,
    private val operationsAuthorizer: InternalOperationsAuthorizer,
    private val auditService: InternalOperationAuditService,
) {

    @GetMapping("/metrics")
    @Operation(summary = "아웃박스 지표 조회", description = "pending, published, retrying, failed 이벤트 수를 조회합니다.")
    fun metrics(
        @RequestHeader(name = InternalOperationsAuthorizer.TOKEN_HEADER, required = false) replayToken: String?,
    ): ResponseEntity<PaymentOutboxMetricsResponse> {
        operationsAuthorizer.authorize(replayToken)
        return ResponseEntity.ok(paymentOutboxService.metrics())
    }

    @PostMapping("/publish")
    @Operation(summary = "대기 아웃박스 발행", description = "대기 중인 아웃박스를 로컬 publisher 경계로 발행합니다.")
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
    @Operation(summary = "실패 아웃박스 재시도", description = "실패한 아웃박스를 PENDING으로 되돌려 재처리합니다.")
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
