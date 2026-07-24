package com.sugowslt.paymentcoreapi.repository

import com.sugowslt.paymentcoreapi.entity.PaymentWebhookEvent
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PaymentWebhookEventRepository : JpaRepository<PaymentWebhookEvent, Long> {
    fun findByTransmissionId(transmissionId: String): PaymentWebhookEvent?

    fun countByOutcomeStartingWith(prefix: String): Long
}
