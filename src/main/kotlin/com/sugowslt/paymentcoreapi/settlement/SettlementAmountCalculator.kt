package com.sugowslt.paymentcoreapi.settlement

import java.math.BigDecimal
import java.math.RoundingMode
import org.springframework.stereotype.Component

@Component
class SettlementAmountCalculator {
    fun calculate(amount: BigDecimal, feeRateBps: Int): SettlementCalculation {
        require(amount.compareTo(BigDecimal.ZERO) > 0) {
            "amount must be greater than zero"
        }
        require(amount.scale() <= MONEY_SCALE) {
            "amount must have at most $MONEY_SCALE decimal places"
        }
        require(feeRateBps in MIN_FEE_RATE_BPS..MAX_FEE_RATE_BPS) {
            "feeRateBps must be between $MIN_FEE_RATE_BPS and $MAX_FEE_RATE_BPS"
        }

        val grossAmount = amount.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY)
        val feeAmount = grossAmount
            .multiply(BigDecimal.valueOf(feeRateBps.toLong()))
            .divide(BASIS_POINTS, MONEY_SCALE, FEE_ROUNDING_MODE)
        val settlementAmount = grossAmount
            .subtract(feeAmount)
            .setScale(MONEY_SCALE, RoundingMode.UNNECESSARY)

        return SettlementCalculation(
            grossAmount = grossAmount,
            feeRateBps = feeRateBps,
            feeAmount = feeAmount,
            settlementAmount = settlementAmount,
        )
    }

    private companion object {
        const val MONEY_SCALE = 2
        const val MIN_FEE_RATE_BPS = 0
        const val MAX_FEE_RATE_BPS = 10_000
        val BASIS_POINTS: BigDecimal = BigDecimal.valueOf(10_000)
        val FEE_ROUNDING_MODE: RoundingMode = RoundingMode.HALF_UP
    }
}
