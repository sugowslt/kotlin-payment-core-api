package com.sugowslt.paymentcoreapi.exception

import java.time.LocalDateTime

data class ApiErrorResponse(
    val timestamp: LocalDateTime,
    val code: String,
    val message: String,
    val path: String,
    val traceId: String,
    val details: List<String> = emptyList(),
)
