package com.sugowslt.paymentcoreapi.repository

import com.sugowslt.paymentcoreapi.entity.PaymentSettlement
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PaymentSettlementRepository : JpaRepository<PaymentSettlement, Long> {
    fun findByPaymentId(paymentId: Long): PaymentSettlement?
}
