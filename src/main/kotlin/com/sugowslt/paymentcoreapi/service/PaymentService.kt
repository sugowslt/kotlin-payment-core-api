package com.sugowslt.paymentcoreapi.service

import com.sugowslt.paymentcoreapi.controller.dto.CreatePaymentRequest
import com.sugowslt.paymentcoreapi.controller.dto.CreatePaymentResponse
import com.sugowslt.paymentcoreapi.controller.dto.CancelPaymentRequest
import com.sugowslt.paymentcoreapi.controller.dto.GetPaymentsCursorResponse
import com.sugowslt.paymentcoreapi.controller.dto.GetPaymentsResponse
import com.sugowslt.paymentcoreapi.controller.dto.PaymentSummary
import com.sugowslt.paymentcoreapi.entity.Payment
import com.sugowslt.paymentcoreapi.entity.PaymentCancellation
import com.sugowslt.paymentcoreapi.entity.PaymentStatus
import com.sugowslt.paymentcoreapi.exception.DuplicatePaymentException
import com.sugowslt.paymentcoreapi.exception.InvalidPaymentStatusTransitionException
import com.sugowslt.paymentcoreapi.exception.InvalidPaymentCancellationException
import com.sugowslt.paymentcoreapi.exception.PaymentIdempotencyInProgressException
import com.sugowslt.paymentcoreapi.exception.PaymentNotFoundException
import com.sugowslt.paymentcoreapi.gateway.PaymentGateway
import com.sugowslt.paymentcoreapi.gateway.PaymentGatewayApprovalRequest
import com.sugowslt.paymentcoreapi.gateway.PaymentGatewayCancellationRequest
import com.sugowslt.paymentcoreapi.gateway.PaymentGatewayRejectedException
import com.sugowslt.paymentcoreapi.repository.PaymentRepository
import com.sugowslt.paymentcoreapi.repository.PaymentCancellationRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.beans.factory.annotation.Autowired

@Service
class PaymentService @Autowired constructor(
    private val paymentRepository: PaymentRepository,
    private val paymentGateway: PaymentGateway,
    private val paymentOutboxService: PaymentOutboxService,
    private val paymentCancellationRepository: PaymentCancellationRepository,
    private val paymentIdempotencyGuard: PaymentIdempotencyGuard,
) {

    constructor(
        paymentRepository: PaymentRepository,
        paymentGateway: PaymentGateway,
        paymentOutboxService: PaymentOutboxService,
        paymentCancellationRepository: PaymentCancellationRepository,
    ) : this(
        paymentRepository,
        paymentGateway,
        paymentOutboxService,
        paymentCancellationRepository,
        NoopPaymentIdempotencyGuard(),
    )

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
        val normalizedKey = approvalIdempotencyKey.trim()
        require(normalizedKey.isNotEmpty()) { "Idempotency-Key must not be blank" }

        val acquireResult = paymentIdempotencyGuard.tryAcquire(paymentId, normalizedKey)
        if (acquireResult == PaymentIdempotencyAcquireResult.InProgress) {
            throw PaymentIdempotencyInProgressException(
                "approval request is already in progress for payment id=$paymentId",
            )
        }

        val lease = (acquireResult as? PaymentIdempotencyAcquireResult.Acquired)?.lease
        try {
            val payment = paymentRepository.findByIdAndDeletedFalseForUpdate(paymentId)
                ?: throw PaymentNotFoundException("payment not found. id=$paymentId")

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
        } finally {
            lease?.let(paymentIdempotencyGuard::release)
        }
    }

    @Transactional(noRollbackFor = [PaymentGatewayRejectedException::class])
    fun cancelPayment(
        paymentId: Long,
        cancellationIdempotencyKey: String = "payment-cancel-$paymentId",
        request: CancelPaymentRequest = CancelPaymentRequest(),
    ): CreatePaymentResponse {
        val payment = paymentRepository.findByIdAndDeletedFalse(paymentId)
            ?: throw PaymentNotFoundException("payment not found. id=$paymentId")

        val normalizedKey = cancellationIdempotencyKey.trim()
        if (normalizedKey.isEmpty()) {
            throw InvalidPaymentCancellationException("Idempotency-Key must not be blank")
        }

        val existingCancellation = paymentCancellationRepository.findByCancellationIdempotencyKey(normalizedKey)
        if (existingCancellation != null) {
            if (existingCancellation.paymentId == payment.id) {
                return payment.toResponse()
            }
            throw InvalidPaymentCancellationException("cancellation idempotency key is already used")
        }

        if (payment.status == PaymentStatus.CANCELED) {
            if (payment.cancellationIdempotencyKey == normalizedKey) {
                return payment.toResponse()
            }
            throw InvalidPaymentStatusTransitionException(
                "payment is already canceled with a different idempotency key",
            )
        }

        if (payment.status != PaymentStatus.APPROVED) {
            throw InvalidPaymentStatusTransitionException(
                "cannot cancel payment in status=${payment.status}",
            )
        }

        val cancelReason = request.cancelReason.trim()
        if (cancelReason.isEmpty()) {
            throw InvalidPaymentCancellationException("cancel reason must not be blank")
        }

        val remainingAmount = payment.amount.subtract(payment.canceledAmount)
        val cancelAmount = request.cancelAmount ?: remainingAmount
        if (cancelAmount <= java.math.BigDecimal.ZERO || cancelAmount > remainingAmount) {
            throw InvalidPaymentCancellationException(
                "cancel amount must be greater than zero and no greater than remaining amount=$remainingAmount",
            )
        }

        val gatewayResult = paymentGateway.cancel(
            PaymentGatewayCancellationRequest(
                paymentId = payment.id,
                providerTransactionId = payment.providerTransactionId,
                cancelReason = cancelReason,
                cancelAmount = cancelAmount,
                cancellationIdempotencyKey = normalizedKey,
            ),
        )
        payment.applyCancellation(cancelAmount, normalizedKey)
        paymentCancellationRepository.save(
            PaymentCancellation(
                paymentId = payment.id,
                cancellationIdempotencyKey = normalizedKey,
                cancelAmount = cancelAmount,
                cancelReason = cancelReason,
                providerCancellationId = gatewayResult.providerCancellationId,
            ),
        )
        paymentOutboxService.enqueuePaymentEvent(payment, "PAYMENT_CANCELED")

        return payment.toResponse()
    }

    @Transactional(readOnly = true)
    fun getPayment(paymentId: Long): CreatePaymentResponse {
        val found = paymentRepository.findByIdAndDeletedFalse(paymentId)
            ?: throw PaymentNotFoundException("payment not found. id=$paymentId")

        return found.toResponse()
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

        return saved.toResponse()
    }

    private fun Payment.toResponse() = CreatePaymentResponse(
        id = id,
        orderId = orderId,
        idempotencyKey = idempotencyKey,
        amount = amount,
        method = method,
        status = status,
        canceledAmount = canceledAmount,
        createdAt = createdAt,
    )
}
