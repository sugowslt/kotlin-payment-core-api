package com.sugowslt.paymentcoreapi.service

import com.sugowslt.paymentcoreapi.controller.dto.InternalOperationAuditResponse
import com.sugowslt.paymentcoreapi.entity.InternalOperationAuditEvent
import com.sugowslt.paymentcoreapi.entity.InternalOperationAuditOutcome
import com.sugowslt.paymentcoreapi.repository.InternalOperationAuditEventRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
class InternalOperationAuditService(
    private val auditEventRepository: InternalOperationAuditEventRepository,
) {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun record(
        operation: String,
        targetId: String?,
        outcome: InternalOperationAuditOutcome,
        traceId: String?,
        detail: String? = null,
    ) {
        auditEventRepository.save(
            InternalOperationAuditEvent(
                operationName = operation,
                targetId = targetId,
                outcome = outcome,
                traceId = traceId,
                detail = detail?.take(500),
            ),
        )
    }

    @Transactional(readOnly = true)
    fun recent(): List<InternalOperationAuditResponse> {
        return auditEventRepository.findTop50ByOrderByCreatedAtDesc()
            .map(InternalOperationAuditResponse::from)
    }
}
