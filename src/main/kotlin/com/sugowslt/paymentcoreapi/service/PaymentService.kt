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
import com.sugowslt.paymentcoreapi.repository.PaymentRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PaymentService(
    private val paymentRepository: PaymentRepository,
) {

    @Transactional
    fun deletePayment(paymentId: Long) {
        val payment = paymentRepository.findByIdAndDeletedFalse(paymentId)
            ?: throw PaymentNotFoundException("payment not found. id=$paymentId")

        payment.markDeleted()
    }

    @Transactional
    fun approvePayment(paymentId: Long): CreatePaymentResponse {
        val payment = paymentRepository.findByIdAndDeletedFalse(paymentId)
            ?: throw PaymentNotFoundException("payment not found. id=$paymentId")

        if (payment.status != PaymentStatus.PENDING) {
            throw InvalidPaymentStatusTransitionException(
                "cannot approve payment in status=${payment.status}",
            )
        }

        payment.approve()

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
}
