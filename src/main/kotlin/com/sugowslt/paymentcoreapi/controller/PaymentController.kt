package com.sugowslt.paymentcoreapi.controller

import com.sugowslt.paymentcoreapi.controller.dto.CreatePaymentRequest
import com.sugowslt.paymentcoreapi.controller.dto.CreatePaymentResponse
import com.sugowslt.paymentcoreapi.controller.dto.GetPaymentsCursorResponse
import com.sugowslt.paymentcoreapi.controller.dto.GetPaymentsResponse
import com.sugowslt.paymentcoreapi.controller.dto.PaymentSummary
import com.sugowslt.paymentcoreapi.service.PaymentService
import jakarta.validation.Valid
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/payments")
class PaymentController(
    private val paymentService: PaymentService,
) {

    @PostMapping("/{paymentId}/approve")
    fun approvePayment(@PathVariable paymentId: Long): ResponseEntity<CreatePaymentResponse> {
        val approved = paymentService.approvePayment(paymentId)
        return ResponseEntity.ok(approved)
    }

    @PostMapping("/{paymentId}/cancel")
    fun cancelPayment(@PathVariable paymentId: Long): ResponseEntity<CreatePaymentResponse> {
        val canceled = paymentService.cancelPayment(paymentId)
        return ResponseEntity.ok(canceled)
    }

    @DeleteMapping("/{paymentId}")
    fun deletePayment(@PathVariable paymentId: Long): ResponseEntity<Void> {
        paymentService.deletePayment(paymentId)
        return ResponseEntity.noContent().build()
    }

    @GetMapping
    fun getPayments(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<GetPaymentsResponse> {
        val pageable = PageRequest.of(page, size, Sort.by("createdAt").descending())
        val result = paymentService.getPayments(pageable)
        return ResponseEntity.ok(result)
    }

    @GetMapping("/cursor")
    fun getPaymentsByCursor(
        @RequestParam(required = false) cursorId: Long?,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<GetPaymentsCursorResponse> {
        val result = paymentService.getPaymentsByCursor(cursorId, size)
        return ResponseEntity.ok(result)
    }

    @GetMapping("/{paymentId}")
    fun getPayment(@PathVariable paymentId: Long): ResponseEntity<CreatePaymentResponse> {
        val payment = paymentService.getPayment(paymentId)
        return ResponseEntity.ok(payment)
    }

    @PostMapping
    fun createPayment(@Valid @RequestBody request: CreatePaymentRequest): ResponseEntity<CreatePaymentResponse> {
        val created = paymentService.createPayment(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(created)
    }
}
