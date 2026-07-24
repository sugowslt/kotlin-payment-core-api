package com.sugowslt.paymentcoreapi.repository

import com.sugowslt.paymentcoreapi.entity.OutboxStatus
import com.sugowslt.paymentcoreapi.entity.PaymentOutboxEvent
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
interface PaymentOutboxEventRepository : JpaRepository<PaymentOutboxEvent, Long> {
    fun countByStatus(status: OutboxStatus): Long

    fun countByStatusAndRetryCountGreaterThan(status: OutboxStatus, retryCount: Int): Long

    fun findTop50ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
        status: OutboxStatus,
        now: LocalDateTime,
    ): List<PaymentOutboxEvent>
}
