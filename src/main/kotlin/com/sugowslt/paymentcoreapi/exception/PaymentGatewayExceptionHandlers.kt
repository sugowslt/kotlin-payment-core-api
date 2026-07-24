package com.sugowslt.paymentcoreapi.exception

import com.sugowslt.paymentcoreapi.filter.TraceIdFilter
import com.sugowslt.paymentcoreapi.gateway.PaymentGatewayRejectedException
import com.sugowslt.paymentcoreapi.gateway.PaymentGatewayRequestException
import com.sugowslt.paymentcoreapi.gateway.PaymentGatewayUnavailableException
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.LocalDateTime

@RestControllerAdvice
class PaymentGatewayExceptionHandlers {

    @ExceptionHandler(PaymentGatewayRequestException::class)
    fun handleInvalidRequest(
        ex: PaymentGatewayRequestException,
        request: HttpServletRequest,
    ): ResponseEntity<ApiErrorResponse> {
        return response(HttpStatus.BAD_REQUEST, "PAYMENT_GATEWAY_REQUEST_INVALID", ex.message, request)
    }

    @ExceptionHandler(PaymentGatewayRejectedException::class)
    fun handleRejected(
        ex: PaymentGatewayRejectedException,
        request: HttpServletRequest,
    ): ResponseEntity<ApiErrorResponse> {
        return response(HttpStatus.PAYMENT_REQUIRED, "PAYMENT_GATEWAY_REJECTED", ex.message, request)
    }

    @ExceptionHandler(PaymentGatewayUnavailableException::class)
    fun handleUnavailable(
        ex: PaymentGatewayUnavailableException,
        request: HttpServletRequest,
    ): ResponseEntity<ApiErrorResponse> {
        return response(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_GATEWAY_UNAVAILABLE", ex.message, request)
    }

    private fun response(
        status: HttpStatus,
        code: String,
        message: String?,
        request: HttpServletRequest,
    ): ResponseEntity<ApiErrorResponse> {
        val traceId = request.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE)?.toString()
            ?.takeUnless { it.isBlank() }
            ?: "UNKNOWN"

        return ResponseEntity.status(status)
            .body(
                ApiErrorResponse(
                    timestamp = LocalDateTime.now(),
                    code = code,
                    message = message ?: code,
                    path = request.requestURI,
                    traceId = traceId,
                ),
            )
    }
}
