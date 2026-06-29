package com.sugowslt.paymentcoreapi.exception

import com.sugowslt.paymentcoreapi.filter.TraceIdFilter
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.ConstraintViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.LocalDateTime

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(
        ex: MethodArgumentNotValidException,
        request: HttpServletRequest,
    ): ResponseEntity<ApiErrorResponse> {
        val details = ex.bindingResult.allErrors.map {
            val fieldName = (it as? FieldError)?.field
            if (fieldName == null) {
                it.defaultMessage ?: "validation error"
            } else {
                "$fieldName: ${it.defaultMessage ?: "invalid value"}"
            }
        }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(
                ApiErrorResponse(
                    timestamp = LocalDateTime.now(),
                    code = "VALIDATION_ERROR",
                    message = "request validation failed",
                    path = request.requestURI,
                    traceId = traceIdOf(request),
                    details = details,
                ),
            )
    }

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolation(
        ex: ConstraintViolationException,
        request: HttpServletRequest,
    ): ResponseEntity<ApiErrorResponse> {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(
                ApiErrorResponse(
                    timestamp = LocalDateTime.now(),
                    code = "VALIDATION_ERROR",
                    message = "constraint violation",
                    path = request.requestURI,
                    traceId = traceIdOf(request),
                    details = ex.constraintViolations.map { it.message },
                ),
            )
    }

    @ExceptionHandler(DuplicatePaymentException::class)
    fun handleDuplicatePayment(
        ex: DuplicatePaymentException,
        request: HttpServletRequest,
    ): ResponseEntity<ApiErrorResponse> {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(
                ApiErrorResponse(
                    timestamp = LocalDateTime.now(),
                    code = "DUPLICATE_PAYMENT",
                    message = ex.message ?: "duplicate payment",
                    path = request.requestURI,
                    traceId = traceIdOf(request),
                ),
            )
    }

    @ExceptionHandler(InvalidPaymentStatusTransitionException::class)
    fun handleInvalidPaymentStatusTransition(
        ex: InvalidPaymentStatusTransitionException,
        request: HttpServletRequest,
    ): ResponseEntity<ApiErrorResponse> {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(
                ApiErrorResponse(
                    timestamp = LocalDateTime.now(),
                    code = "INVALID_PAYMENT_STATUS_TRANSITION",
                    message = ex.message ?: "invalid payment status transition",
                    path = request.requestURI,
                    traceId = traceIdOf(request),
                ),
            )
    }

    @ExceptionHandler(PaymentNotFoundException::class)
    fun handlePaymentNotFound(
        ex: PaymentNotFoundException,
        request: HttpServletRequest,
    ): ResponseEntity<ApiErrorResponse> {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(
                ApiErrorResponse(
                    timestamp = LocalDateTime.now(),
                    code = "PAYMENT_NOT_FOUND",
                    message = ex.message ?: "payment not found",
                    path = request.requestURI,
                    traceId = traceIdOf(request),
                ),
            )
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleNotReadable(
        request: HttpServletRequest,
    ): ResponseEntity<ApiErrorResponse> {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(
                ApiErrorResponse(
                    timestamp = LocalDateTime.now(),
                    code = "INVALID_REQUEST",
                    message = "request body is not readable",
                    path = request.requestURI,
                    traceId = traceIdOf(request),
                ),
            )
    }

    private fun traceIdOf(request: HttpServletRequest): String {
        val traceId = request.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE)?.toString()
        return if (traceId.isNullOrBlank()) "UNKNOWN" else traceId
    }
}
