package com.sugowslt.paymentcoreapi.controller

import com.sugowslt.paymentcoreapi.controller.dto.InternalOperationAuditResponse
import com.sugowslt.paymentcoreapi.security.InternalOperationsAuthorizer
import com.sugowslt.paymentcoreapi.service.InternalOperationAuditService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/internal/audit-events")
@Tag(name = "Internal Audit", description = "보호된 내부 운영 감사 이력 API")
class InternalOperationAuditController(
    private val auditService: InternalOperationAuditService,
    private val operationsAuthorizer: InternalOperationsAuthorizer,
) {

    @GetMapping
    @Operation(summary = "내부 운영 감사 이력 조회", description = "최근 replay·outbox 운영 작업과 traceId를 조회합니다.")
    fun recent(
        @RequestHeader(name = InternalOperationsAuthorizer.TOKEN_HEADER, required = false) replayToken: String?,
    ): ResponseEntity<List<InternalOperationAuditResponse>> {
        operationsAuthorizer.authorize(replayToken)
        return ResponseEntity.ok(auditService.recent())
    }
}
