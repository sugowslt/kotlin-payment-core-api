package com.sugowslt.paymentcoreapi.controller

import com.sugowslt.paymentcoreapi.controller.dto.InternalOperationAuditResponse
import com.sugowslt.paymentcoreapi.security.InternalOperationsAuthorizer
import com.sugowslt.paymentcoreapi.service.InternalOperationAuditService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/internal/audit-events")
class InternalOperationAuditController(
    private val auditService: InternalOperationAuditService,
    private val operationsAuthorizer: InternalOperationsAuthorizer,
) {

    @GetMapping
    fun recent(
        @RequestHeader(name = InternalOperationsAuthorizer.TOKEN_HEADER, required = false) replayToken: String?,
    ): ResponseEntity<List<InternalOperationAuditResponse>> {
        operationsAuthorizer.authorize(replayToken)
        return ResponseEntity.ok(auditService.recent())
    }
}
