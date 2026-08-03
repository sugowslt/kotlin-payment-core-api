package com.sugowslt.paymentcoreapi.settlement

import com.sugowslt.paymentcoreapi.entity.Payment
import com.sugowslt.paymentcoreapi.entity.PaymentSettlement

interface SettlementLedger {
    fun createRequestedSnapshot(payment: Payment): PaymentSettlement?
}
