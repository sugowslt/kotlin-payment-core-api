package com.sugowslt.paymentcoreapi.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.sugowslt.paymentcoreapi.controller.dto.PaymentOutboxMetricsResponse
import com.sugowslt.paymentcoreapi.controller.dto.PaymentOutboxRetryResponse
import com.sugowslt.paymentcoreapi.entity.OutboxStatus
import com.sugowslt.paymentcoreapi.entity.Payment
import com.sugowslt.paymentcoreapi.entity.PaymentOutboxEvent
import com.sugowslt.paymentcoreapi.exception.InvalidOutboxStatusException
import com.sugowslt.paymentcoreapi.exception.OutboxEventNotFoundException
import com.sugowslt.paymentcoreapi.repository.PaymentOutboxEventRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class PaymentOutboxService(
    private val paymentOutboxEventRepository: PaymentOutboxEventRepository,
    private val objectMapper: ObjectMapper,
    private val paymentOutboxPublisher: PaymentOutboxPublisher,
    private val outboxOperationsProperties: OutboxOperationsProperties,
) {

    @Transactional
    fun enqueuePaymentEvent(payment: Payment, eventType: String) {
        paymentOutboxEventRepository.save(
            PaymentOutboxEvent(
                aggregateId = payment.id,
                eventType = eventType,
                payload = objectMapper.writeValueAsString(
                    mapOf(
                        "paymentId" to payment.id,
                        "orderId" to payment.orderId,
                        "status" to payment.status.name,
                        "providerTransactionId" to payment.providerTransactionId,
                    ),
                ),
            ),
        )
    }

    @Transactional(readOnly = true)
    fun metrics(): PaymentOutboxMetricsResponse {
        return PaymentOutboxMetricsResponse(
            pendingEvents = paymentOutboxEventRepository.countByStatus(OutboxStatus.PENDING),
            retryingEvents = paymentOutboxEventRepository
                .countByStatusAndRetryCountGreaterThan(OutboxStatus.PENDING, 0),
            publishedEvents = paymentOutboxEventRepository.countByStatus(OutboxStatus.PUBLISHED),
            failedEvents = paymentOutboxEventRepository.countByStatus(OutboxStatus.FAILED),
        )
    }

    @Transactional
    fun publishPending(): PaymentOutboxMetricsResponse {
        val now = LocalDateTime.now()
        paymentOutboxEventRepository
            .findTop50ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(OutboxStatus.PENDING, now)
            .forEach { event ->
                try {
                    paymentOutboxPublisher.publish(event)
                    event.status = OutboxStatus.PUBLISHED
                    event.publishedAt = now
                    event.lastError = null
                    event.nextAttemptAt = null
                } catch (ex: Exception) {
                    event.retryCount += 1
                    event.lastError = (ex.message ?: ex.javaClass.simpleName).take(500)

                    if (event.retryCount >= outboxOperationsProperties.maxRetries.coerceAtLeast(1)) {
                        event.status = OutboxStatus.FAILED
                        event.nextAttemptAt = null
                    } else {
                        event.nextAttemptAt = now.plusSeconds(outboxOperationsProperties.retryDelaySeconds.coerceAtLeast(0))
                    }
                }
            }

        return metrics()
    }

    @Transactional
    fun retryFailed(eventId: Long): PaymentOutboxRetryResponse {
        val event = paymentOutboxEventRepository.findById(eventId).orElseThrow {
            OutboxEventNotFoundException(eventId)
        }

        if (event.status != OutboxStatus.FAILED) {
            throw InvalidOutboxStatusException(eventId, event.status)
        }

        event.status = OutboxStatus.PENDING
        event.retryCount = 0
        event.lastError = null
        event.nextAttemptAt = LocalDateTime.now()

        return PaymentOutboxRetryResponse(
            eventId = event.id,
            status = event.status,
            retryCount = event.retryCount,
            nextAttemptAt = event.nextAttemptAt,
        )
    }
}
