package com.sugowslt.paymentcoreapi.settlement

import com.sugowslt.paymentcoreapi.entity.Payment
import com.sugowslt.paymentcoreapi.entity.PaymentSettlement
import com.sugowslt.paymentcoreapi.repository.PaymentSettlementRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SettlementLedgerService(
    private val paymentSettlementRepository: PaymentSettlementRepository,
    private val settlementAmountCalculator: SettlementAmountCalculator,
    private val settlementProperties: SettlementProperties,
) : SettlementLedger {

    @Transactional
    override fun createRequestedSnapshot(payment: Payment): PaymentSettlement {
        paymentSettlementRepository.findByPaymentId(payment.id)?.let { return it }

        val calculation = settlementAmountCalculator.calculate(
            amount = payment.amount,
            feeRateBps = settlementProperties.feeRateBps,
        )

        return paymentSettlementRepository.save(
            PaymentSettlement(
                paymentId = payment.id,
                grossAmount = calculation.grossAmount,
                feeRateBps = calculation.feeRateBps,
                feeAmount = calculation.feeAmount,
                settlementAmount = calculation.settlementAmount,
            ),
        )
    }
}
