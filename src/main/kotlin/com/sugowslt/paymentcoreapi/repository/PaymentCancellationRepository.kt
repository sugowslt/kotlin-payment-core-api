package com.sugowslt.paymentcoreapi.repository

import com.sugowslt.paymentcoreapi.entity.PaymentCancellation
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PaymentCancellationRepository : JpaRepository<PaymentCancellation, Long> {
    fun findByCancellationIdempotencyKey(cancellationIdempotencyKey: String): PaymentCancellation?
}
