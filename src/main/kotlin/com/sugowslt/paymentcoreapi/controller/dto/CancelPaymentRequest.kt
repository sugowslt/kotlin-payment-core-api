package com.sugowslt.paymentcoreapi.controller.dto

import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.math.BigDecimal

data class CancelPaymentRequest(
    @field:NotBlank
    @field:Size(max = 200)
    val cancelReason: String = "customer requested cancellation",

    @field:DecimalMin(value = "0.01")
    val cancelAmount: BigDecimal? = null,
)
