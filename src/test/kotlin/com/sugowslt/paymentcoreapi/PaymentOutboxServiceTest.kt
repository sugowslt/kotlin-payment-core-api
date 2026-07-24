package com.sugowslt.paymentcoreapi

import com.fasterxml.jackson.databind.ObjectMapper
import com.sugowslt.paymentcoreapi.entity.OutboxStatus
import com.sugowslt.paymentcoreapi.entity.PaymentOutboxEvent
import com.sugowslt.paymentcoreapi.repository.PaymentOutboxEventRepository
import com.sugowslt.paymentcoreapi.service.OutboxOperationsProperties
import com.sugowslt.paymentcoreapi.service.PaymentOutboxPublisher
import com.sugowslt.paymentcoreapi.service.PaymentOutboxService
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class PaymentOutboxServiceTest {

    @MockK(relaxed = true)
    lateinit var paymentOutboxEventRepository: PaymentOutboxEventRepository

    @MockK
    lateinit var paymentOutboxPublisher: PaymentOutboxPublisher

    private lateinit var paymentOutboxService: PaymentOutboxService

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        paymentOutboxService = PaymentOutboxService(
            paymentOutboxEventRepository,
            ObjectMapper(),
            paymentOutboxPublisher,
            OutboxOperationsProperties(maxRetries = 2, retryDelaySeconds = 0),
        )
    }

    @Test
    fun `publish pending keeps event pending while retry remains`() {
        val event = pendingEvent()
        every {
            paymentOutboxEventRepository
                .findTop50ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(any(), any())
        } returns listOf(event)
        every { paymentOutboxPublisher.publish(event) } throws IllegalStateException("broker unavailable")

        paymentOutboxService.publishPending()

        assertEquals(OutboxStatus.PENDING, event.status)
        assertEquals(1, event.retryCount)
        assertEquals("broker unavailable", event.lastError)
        checkNotNull(event.nextAttemptAt)
    }

    @Test
    fun `publish pending marks event failed after maximum retries`() {
        val event = pendingEvent()
        every {
            paymentOutboxEventRepository
                .findTop50ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(any(), any())
        } returns listOf(event)
        every { paymentOutboxPublisher.publish(event) } throws IllegalStateException("broker unavailable")

        paymentOutboxService.publishPending()
        paymentOutboxService.publishPending()

        assertEquals(OutboxStatus.FAILED, event.status)
        assertEquals(2, event.retryCount)
        assertEquals("broker unavailable", event.lastError)
        assertEquals(null, event.nextAttemptAt)
        verify(exactly = 2) { paymentOutboxPublisher.publish(event) }
    }

    @Test
    fun `publish pending marks event published when publisher succeeds`() {
        val event = pendingEvent()
        every {
            paymentOutboxEventRepository
                .findTop50ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(any(), any())
        } returns listOf(event)
        every { paymentOutboxPublisher.publish(event) } returns Unit

        paymentOutboxService.publishPending()

        assertEquals(OutboxStatus.PUBLISHED, event.status)
        assertEquals(null, event.lastError)
        assertEquals(null, event.nextAttemptAt)
        checkNotNull(event.publishedAt)
    }

    private fun pendingEvent() = PaymentOutboxEvent(
        id = 1,
        aggregateId = 10,
        eventType = "PAYMENT_CREATED",
        payload = "{}",
        createdAt = LocalDateTime.now(),
    )
}
