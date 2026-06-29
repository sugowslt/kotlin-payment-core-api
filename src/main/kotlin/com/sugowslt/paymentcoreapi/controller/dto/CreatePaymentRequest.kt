package com.sugowslt.paymentcoreapi.controller.dto

import com.sugowslt.paymentcoreapi.entity.PaymentMethod
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import java.math.BigDecimal

data class CreatePaymentRequest(
    @field:Positive(message = "orderId must be greater than 0")
    val orderId: Long,

    @field:NotBlank(message = "idempotencyKey is required")
    val idempotencyKey: String,

    @field:NotNull(message = "amount is required")
    @field:DecimalMin(value = "0.01", message = "amount must be greater than 0")
    val amount: BigDecimal,

    @field:NotNull(message = "method is required")
    val method: PaymentMethod,
)
