package com.sugowslt.paymentcoreapi.controller

import com.sugowslt.paymentcoreapi.controller.dto.CreatePaymentRequest
import com.sugowslt.paymentcoreapi.controller.dto.CreatePaymentResponse
import com.sugowslt.paymentcoreapi.controller.dto.CancelPaymentRequest
import com.sugowslt.paymentcoreapi.controller.dto.GetPaymentsCursorResponse
import com.sugowslt.paymentcoreapi.controller.dto.GetPaymentsResponse
import com.sugowslt.paymentcoreapi.controller.dto.PaymentSummary
import com.sugowslt.paymentcoreapi.service.PaymentService
import jakarta.validation.Valid
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/payments")
@Tag(name = "Payments", description = "결제 생성, 조회, 승인, 취소 API")
class PaymentController(
    private val paymentService: PaymentService,
) {

    @PostMapping("/{paymentId}/approve")
    @Operation(summary = "결제 승인", description = "Idempotency-Key로 승인 재요청을 안전하게 처리합니다.")
    fun approvePayment(
        @PathVariable paymentId: Long,
        @RequestHeader("Idempotency-Key") approvalIdempotencyKey: String,
        @RequestHeader(name = "Payment-Key", required = false) paymentKey: String?,
    ): ResponseEntity<CreatePaymentResponse> {
        val approved = paymentService.approvePayment(paymentId, approvalIdempotencyKey, paymentKey)
        return ResponseEntity.ok(approved)
    }

    @PostMapping("/{paymentId}/cancel")
    @Operation(summary = "결제 취소", description = "Idempotency-Key와 선택적 부분 취소 금액으로 결제를 취소합니다.")
    fun cancelPayment(
        @PathVariable paymentId: Long,
        @RequestHeader("Idempotency-Key") cancellationIdempotencyKey: String,
        @Valid @RequestBody(required = false) request: CancelPaymentRequest?,
    ): ResponseEntity<CreatePaymentResponse> {
        val canceled = paymentService.cancelPayment(
            paymentId,
            cancellationIdempotencyKey,
            request ?: CancelPaymentRequest(),
        )
        return ResponseEntity.ok(canceled)
    }

    @DeleteMapping("/{paymentId}")
    @Operation(summary = "결제 삭제", description = "결제를 soft delete 처리합니다.")
    fun deletePayment(@PathVariable paymentId: Long): ResponseEntity<Void> {
        paymentService.deletePayment(paymentId)
        return ResponseEntity.noContent().build()
    }

    @GetMapping
    @Operation(summary = "결제 목록 조회", description = "createdAt 내림차순의 offset 기반 결제 목록을 조회합니다.")
    fun getPayments(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<GetPaymentsResponse> {
        val pageable = PageRequest.of(page, size, Sort.by("createdAt").descending())
        val result = paymentService.getPayments(pageable)
        return ResponseEntity.ok(result)
    }

    @GetMapping("/cursor")
    @Operation(summary = "결제 cursor 조회", description = "payment id keyset cursor 기반으로 다음 결제 페이지를 조회합니다.")
    fun getPaymentsByCursor(
        @RequestParam(required = false) cursorId: Long?,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<GetPaymentsCursorResponse> {
        val result = paymentService.getPaymentsByCursor(cursorId, size)
        return ResponseEntity.ok(result)
    }

    @GetMapping("/{paymentId}")
    @Operation(summary = "결제 단건 조회")
    fun getPayment(@PathVariable paymentId: Long): ResponseEntity<CreatePaymentResponse> {
        val payment = paymentService.getPayment(paymentId)
        return ResponseEntity.ok(payment)
    }

    @PostMapping
    @Operation(summary = "결제 생성", description = "주문 식별자와 결제 금액을 검증한 뒤 PENDING 결제를 생성합니다.")
    fun createPayment(@Valid @RequestBody request: CreatePaymentRequest): ResponseEntity<CreatePaymentResponse> {
        val created = paymentService.createPayment(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(created)
    }
}
