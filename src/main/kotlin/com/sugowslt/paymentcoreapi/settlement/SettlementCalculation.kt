package com.sugowslt.paymentcoreapi.settlement

import java.math.BigDecimal

data class SettlementCalculation(
    val grossAmount: BigDecimal,
    val feeRateBps: Int,
    val feeAmount: BigDecimal,
    val settlementAmount: BigDecimal,
)
