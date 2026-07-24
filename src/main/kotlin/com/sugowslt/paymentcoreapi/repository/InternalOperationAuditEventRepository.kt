package com.sugowslt.paymentcoreapi.repository

import com.sugowslt.paymentcoreapi.entity.InternalOperationAuditEvent
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface InternalOperationAuditEventRepository : JpaRepository<InternalOperationAuditEvent, Long> {
    fun findTop50ByOrderByCreatedAtDesc(): List<InternalOperationAuditEvent>
}
