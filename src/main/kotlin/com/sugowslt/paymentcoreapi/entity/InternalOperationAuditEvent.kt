package com.sugowslt.paymentcoreapi.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.LocalDateTime

enum class InternalOperationAuditOutcome {
    SUCCESS,
    FAILED,
}

@Entity
@Table(
    name = "internal_operation_audit_events",
    indexes = [Index(name = "idx_internal_audit_created_at", columnList = "created_at")],
)
class InternalOperationAuditEvent(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "operation_name", nullable = false, length = 80)
    val operationName: String,

    @Column(name = "target_id", length = 120)
    val targetId: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val outcome: InternalOperationAuditOutcome,

    @Column(name = "trace_id", length = 120)
    val traceId: String? = null,

    @Column(name = "detail", length = 500)
    val detail: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
)
