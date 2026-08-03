package com.sugowslt.paymentcoreapi.controller

import com.sugowslt.paymentcoreapi.controller.dto.PaymentWebhookMetricsResponse
import com.sugowslt.paymentcoreapi.controller.dto.PaymentWebhookResponse
import com.sugowslt.paymentcoreapi.entity.InternalOperationAuditOutcome
import com.sugowslt.paymentcoreapi.filter.TraceIdFilter
import com.sugowslt.paymentcoreapi.security.InternalOperationsAuthorizer
import com.sugowslt.paymentcoreapi.service.InternalOperationAuditService
import com.sugowslt.paymentcoreapi.service.PaymentWebhookService
import jakarta.servlet.http.HttpServletRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/internal/webhooks")
@Tag(name = "Internal Webhook Operations", description = "보호된 웹훅 replay·운영 지표 API")
class PaymentWebhookOperationsController(
    private val paymentWebhookService: PaymentWebhookService,
    private val operationsAuthorizer: InternalOperationsAuthorizer,
    private val auditService: InternalOperationAuditService,
) {

    @PostMapping("/{transmissionId}/replay")
    @Operation(summary = "웹훅 replay", description = "운영 token 검증 후 저장된 transmission ID를 다시 처리합니다.")
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
    @Operation(summary = "웹훅 지표 조회", description = "보호된 운영 endpoint에서 웹훅 처리 지표를 조회합니다.")
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
