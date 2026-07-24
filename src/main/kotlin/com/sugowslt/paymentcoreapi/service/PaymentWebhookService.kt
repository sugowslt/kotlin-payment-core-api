package com.sugowslt.paymentcoreapi.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.sugowslt.paymentcoreapi.controller.dto.PaymentWebhookMetricsResponse
import com.sugowslt.paymentcoreapi.controller.dto.PaymentWebhookResponse
import com.sugowslt.paymentcoreapi.controller.dto.TossPaymentStatusWebhookRequest
import com.sugowslt.paymentcoreapi.entity.PaymentStatus
import com.sugowslt.paymentcoreapi.entity.PaymentCancellation
import com.sugowslt.paymentcoreapi.entity.PaymentWebhookEvent
import com.sugowslt.paymentcoreapi.exception.InvalidWebhookException
import com.sugowslt.paymentcoreapi.repository.PaymentRepository
import com.sugowslt.paymentcoreapi.repository.PaymentCancellationRepository
import com.sugowslt.paymentcoreapi.repository.PaymentWebhookEventRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class PaymentWebhookService(
    private val paymentRepository: PaymentRepository,
    private val webhookEventRepository: PaymentWebhookEventRepository,
    private val objectMapper: ObjectMapper,
    private val paymentOutboxService: PaymentOutboxService,
    private val paymentCancellationRepository: PaymentCancellationRepository,
) {

    @Transactional
    fun handleTossPaymentStatusChanged(
        transmissionId: String,
        request: TossPaymentStatusWebhookRequest,
    ): PaymentWebhookResponse {
        val normalizedTransmissionId = transmissionId.trim()
        if (normalizedTransmissionId.isBlank()) {
            throw InvalidWebhookException("webhook transmission id must not be blank")
        }

        if (webhookEventRepository.findByTransmissionId(normalizedTransmissionId) != null) {
            return PaymentWebhookResponse(result = "DUPLICATE")
        }

        val event = webhookEventRepository.save(
            PaymentWebhookEvent(
                transmissionId = normalizedTransmissionId,
                eventType = request.eventType,
                orderId = request.data.orderId,
                providerPaymentKey = request.data.paymentKey,
                providerStatus = request.data.status,
                payload = objectMapper.writeValueAsString(request),
            ),
        )

        return applyEvent(event, request, replay = false)
    }

    @Transactional
    fun replay(transmissionId: String): PaymentWebhookResponse {
        val event = webhookEventRepository.findByTransmissionId(transmissionId.trim())
            ?: throw InvalidWebhookException("webhook event not found. transmissionId=$transmissionId")

        if (event.outcome.startsWith("PROCESSED_") || event.outcome.startsWith("REPROCESSED_")) {
            return PaymentWebhookResponse(result = "ALREADY_PROCESSED")
        }

        val request = try {
            objectMapper.readValue(event.payload, TossPaymentStatusWebhookRequest::class.java)
        } catch (ex: Exception) {
            throw InvalidWebhookException("stored webhook payload cannot be parsed")
        }

        return applyEvent(event, request, replay = true)
    }

    @Transactional(readOnly = true)
    fun metrics(): PaymentWebhookMetricsResponse {
        return PaymentWebhookMetricsResponse(
            totalEvents = webhookEventRepository.count(),
            processedEvents = webhookEventRepository.countByOutcomeStartingWith("PROCESSED_"),
            reprocessedEvents = webhookEventRepository.countByOutcomeStartingWith("REPROCESSED_"),
            ignoredEvents = webhookEventRepository.countByOutcomeStartingWith("IGNORED_"),
        )
    }

    private fun applyEvent(
        event: PaymentWebhookEvent,
        request: TossPaymentStatusWebhookRequest,
        replay: Boolean,
    ): PaymentWebhookResponse {
        val prefix = if (replay) "REPROCESSED_" else ""

        if (request.eventType != "PAYMENT_STATUS_CHANGED") {
            event.outcome = "${prefix}IGNORED_EVENT_TYPE"
            event.processedAt = LocalDateTime.now()
            return PaymentWebhookResponse(result = "IGNORED")
        }

        val orderIdValue = request.data.orderId?.toLongOrNull()
            ?: throw InvalidWebhookException("webhook data.orderId must be a numeric order id")

        val payment = paymentRepository.findByOrderIdAndDeletedFalseForUpdate(orderIdValue)
        if (payment == null) {
            event.outcome = "${prefix}IGNORED_PAYMENT_NOT_FOUND"
            event.processedAt = LocalDateTime.now()
            return PaymentWebhookResponse(result = "IGNORED")
        }

        val baseResult = when (request.data.status) {
            "DONE" -> {
                if (payment.status == PaymentStatus.PENDING && !request.data.paymentKey.isNullOrBlank()) {
                    payment.approve(request.data.paymentKey)
                    paymentOutboxService.enqueuePaymentEvent(payment, "PAYMENT_APPROVED_BY_WEBHOOK")
                    "PROCESSED_APPROVED"
                } else {
                    "IGNORED_STATUS"
                }
            }
            "EXPIRED", "ABORTED" -> {
                if (payment.status == PaymentStatus.PENDING) {
                    payment.markFailed()
                    paymentOutboxService.enqueuePaymentEvent(payment, "PAYMENT_FAILED_BY_WEBHOOK")
                    "PROCESSED_FAILED"
                } else {
                    "IGNORED_STATUS"
                }
            }
            "CANCELED" -> {
                if (payment.status == PaymentStatus.APPROVED) {
                    val cancellationKey = "webhook-${event.transmissionId}"
                    val remainingAmount = payment.amount.subtract(payment.canceledAmount)
                    payment.applyCancellation(remainingAmount, cancellationKey)
                    paymentCancellationRepository.save(
                        PaymentCancellation(
                            paymentId = payment.id,
                            cancellationIdempotencyKey = cancellationKey,
                            cancelAmount = remainingAmount,
                            cancelReason = "Toss status webhook",
                        ),
                    )
                    paymentOutboxService.enqueuePaymentEvent(payment, "PAYMENT_CANCELED_BY_WEBHOOK")
                    "PROCESSED_CANCELED"
                } else {
                    "IGNORED_STATUS"
                }
            }
            else -> "IGNORED_STATUS"
        }

        event.outcome = if (replay) {
            "REPROCESSED_${baseResult.removePrefix("PROCESSED_")}"
        } else {
            baseResult
        }
        event.processedAt = LocalDateTime.now()
        return PaymentWebhookResponse(result = if (replay) event.outcome else baseResult)
    }
}
