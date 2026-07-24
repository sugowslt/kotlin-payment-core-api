package com.sugowslt.paymentcoreapi.controller.dto

import com.sugowslt.paymentcoreapi.entity.InternalOperationAuditEvent
import com.sugowslt.paymentcoreapi.entity.InternalOperationAuditOutcome
import java.time.LocalDateTime

data class InternalOperationAuditResponse(
    val id: Long,
    val operation: String,
    val targetId: String?,
    val outcome: InternalOperationAuditOutcome,
    val traceId: String?,
    val detail: String?,
    val createdAt: LocalDateTime,
) {
    companion object {
        fun from(event: InternalOperationAuditEvent): InternalOperationAuditResponse {
            return InternalOperationAuditResponse(
                id = event.id,
                operation = event.operationName,
                targetId = event.targetId,
                outcome = event.outcome,
                traceId = event.traceId,
                detail = event.detail,
                createdAt = event.createdAt,
            )
        }
    }
}
