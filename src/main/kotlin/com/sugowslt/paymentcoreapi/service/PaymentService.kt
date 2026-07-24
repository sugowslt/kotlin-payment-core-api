package com.sugowslt.paymentcoreapi.service

import com.sugowslt.paymentcoreapi.controller.dto.CreatePaymentRequest
import com.sugowslt.paymentcoreapi.controller.dto.CreatePaymentResponse
import com.sugowslt.paymentcoreapi.controller.dto.GetPaymentsCursorResponse
import com.sugowslt.paymentcoreapi.controller.dto.GetPaymentsResponse
import com.sugowslt.paymentcoreapi.controller.dto.PaymentSummary
import com.sugowslt.paymentcoreapi.entity.Payment
import com.sugowslt.paymentcoreapi.entity.PaymentStatus
import com.sugowslt.paymentcoreapi.exception.DuplicatePaymentException
import com.sugowslt.paymentcoreapi.exception.InvalidPaymentStatusTransitionException
import com.sugowslt.paymentcoreapi.exception.PaymentNotFoundException
import com.sugowslt.paymentcoreapi.gateway.PaymentGateway
import com.sugowslt.paymentcoreapi.gateway.PaymentGatewayApprovalRequest
import com.sugowslt.paymentcoreapi.gateway.PaymentGatewayRejectedException
import com.sugowslt.paymentcoreapi.repository.PaymentRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PaymentService(
    private val paymentRepository: PaymentRepository,
    private val paymentGateway: PaymentGateway,
    private val paymentOutboxService: PaymentOutboxService,
) {

    @Transactional
    fun deletePayment(paymentId: Long) {
        val payment = paymentRepository.findByIdAndDeletedFalse(paymentId)
            ?: throw PaymentNotFoundException("payment not found. id=$paymentId")

        payment.markDeleted()
    }

    @Transactional(noRollbackFor = [PaymentGatewayRejectedException::class])
    fun approvePayment(
        paymentId: Long,
        approvalIdempotencyKey: String,
        paymentKey: String? = null,
    ): CreatePaymentResponse {
        val payment = paymentRepository.findByIdAndDeletedFalseForUpdate(paymentId)
            ?: throw PaymentNotFoundException("payment not found. id=$paymentId")

        val normalizedKey = approvalIdempotencyKey.trim()
        require(normalizedKey.isNotEmpty()) { "Idempotency-Key must not be blank" }

        if (payment.status == PaymentStatus.APPROVED) {
            if (payment.approvalIdempotencyKey == normalizedKey) {
                return payment.toResponse()
            }
            throw InvalidPaymentStatusTransitionException(
                "payment is already approved with a different idempotency key",
            )
        }

        if (payment.status != PaymentStatus.PENDING) {
            throw InvalidPaymentStatusTransitionException(
                "cannot approve payment in status=${payment.status}",
            )
        }

        val gatewayResult = try {
            paymentGateway.approve(
                PaymentGatewayApprovalRequest(
                    paymentId = payment.id,
                    orderId = payment.orderId,
                    amount = payment.amount,
                    method = payment.method,
                    approvalIdempotencyKey = normalizedKey,
                    paymentKey = paymentKey?.trim()?.takeUnless { it.isNullOrBlank() },
                ),
            )
        } catch (ex: PaymentGatewayRejectedException) {
            payment.approvalIdempotencyKey = normalizedKey
            payment.markFailed()
            throw ex
        }

        payment.approvalIdempotencyKey = normalizedKey
        payment.approve(gatewayResult.providerTransactionId)
        paymentOutboxService.enqueuePaymentEvent(payment, "PAYMENT_APPROVED")

        return payment.toResponse()
    }

    @Transactional
    fun cancelPayment(paymentId: Long): CreatePaymentResponse {
        val payment = paymentRepository.findByIdAndDeletedFalse(paymentId)
            ?: throw PaymentNotFoundException("payment not found. id=$paymentId")

        if (payment.status != PaymentStatus.APPROVED) {
            throw InvalidPaymentStatusTransitionException(
                "cannot cancel payment in status=${payment.status}",
            )
        }

        payment.cancel()
        paymentOutboxService.enqueuePaymentEvent(payment, "PAYMENT_CANCELED")

        return CreatePaymentResponse(
            id = payment.id,
            orderId = payment.orderId,
            idempotencyKey = payment.idempotencyKey,
            amount = payment.amount,
            method = payment.method,
            status = payment.status,
            createdAt = payment.createdAt,
        )
    }

    @Transactional(readOnly = true)
    fun getPayment(paymentId: Long): CreatePaymentResponse {
        val found = paymentRepository.findByIdAndDeletedFalse(paymentId)
            ?: throw PaymentNotFoundException("payment not found. id=$paymentId")

        return CreatePaymentResponse(
            id = found.id,
            orderId = found.orderId,
            idempotencyKey = found.idempotencyKey,
            amount = found.amount,
            method = found.method,
            status = found.status,
            createdAt = found.createdAt,
        )
    }

    @Transactional(readOnly = true)
    fun getPayments(pageable: Pageable): GetPaymentsResponse {
        val page = paymentRepository.findAllByDeletedFalseOrderByCreatedAtDesc(pageable)

        return GetPaymentsResponse(
            content = page.content.map { payment ->
                PaymentSummary(
                    id = payment.id,
                    orderId = payment.orderId,
                    amount = payment.amount,
                    method = payment.method,
                    status = payment.status,
                    createdAt = payment.createdAt,
                )
            },
            page = page.number,
            size = page.size,
            totalElements = page.totalElements,
            totalPages = page.totalPages,
        )
    }

    @Transactional(readOnly = true)
    fun getPaymentsByCursor(cursorId: Long?, size: Int): GetPaymentsCursorResponse {
        val pageSize = size.coerceIn(1, 100)
        val rows = paymentRepository.findByCursorAndDeletedFalse(cursorId, PageRequest.of(0, pageSize + 1))
        val hasNext = rows.size > pageSize
        val content = if (hasNext) rows.take(pageSize) else rows
        val nextCursorId = if (hasNext) content.last().id else null

        return GetPaymentsCursorResponse(
            content = content.map { payment ->
                PaymentSummary(
                    id = payment.id,
                    orderId = payment.orderId,
                    amount = payment.amount,
                    method = payment.method,
                    status = payment.status,
                    createdAt = payment.createdAt,
                )
            },
            size = pageSize,
            hasNext = hasNext,
            nextCursorId = nextCursorId,
        )
    }

    @Transactional
    fun createPayment(request: CreatePaymentRequest): CreatePaymentResponse {
        val normalizedKey = request.idempotencyKey.trim()
        if (paymentRepository.existsByIdempotencyKeyAndDeletedFalse(normalizedKey)) {
            throw DuplicatePaymentException("payment already exists for idempotencyKey=$normalizedKey")
        }

        val saved = paymentRepository.save(
            Payment(
                orderId = request.orderId,
                idempotencyKey = normalizedKey,
                amount = request.amount,
                method = request.method,
            ),
        )

        paymentOutboxService.enqueuePaymentEvent(saved, "PAYMENT_CREATED")

        return CreatePaymentResponse(
            id = saved.id,
            orderId = saved.orderId,
            idempotencyKey = saved.idempotencyKey,
            amount = saved.amount,
            method = saved.method,
            status = saved.status,
            createdAt = saved.createdAt,
        )
    }

    private fun Payment.toResponse() = CreatePaymentResponse(
        id = id,
        orderId = orderId,
        idempotencyKey = idempotencyKey,
        amount = amount,
        method = method,
        status = status,
        createdAt = createdAt,
    )
}
