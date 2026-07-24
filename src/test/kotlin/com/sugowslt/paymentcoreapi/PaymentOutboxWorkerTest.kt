package com.sugowslt.paymentcoreapi

import com.sugowslt.paymentcoreapi.controller.dto.PaymentOutboxMetricsResponse
import com.sugowslt.paymentcoreapi.service.PaymentOutboxService
import com.sugowslt.paymentcoreapi.service.PaymentOutboxWorker
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PaymentOutboxWorkerTest {

    @MockK
    lateinit var paymentOutboxService: PaymentOutboxService

    private lateinit var worker: PaymentOutboxWorker

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        worker = PaymentOutboxWorker(paymentOutboxService)
    }

    @Test
    fun `worker delegates pending publication to outbox service`() {
        every { paymentOutboxService.publishPending() } returns
            PaymentOutboxMetricsResponse(
                pendingEvents = 0,
                retryingEvents = 0,
                publishedEvents = 1,
                failedEvents = 0,
            )

        worker.publishPending()

        verify(exactly = 1) { paymentOutboxService.publishPending() }
    }
}
