package com.sugowslt.paymentcoreapi.service

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(name = ["payment.outbox.worker.enabled"], havingValue = "true")
class PaymentOutboxWorker(
    private val paymentOutboxService: PaymentOutboxService,
) {

    @Scheduled(fixedDelayString = "\${payment.outbox.worker.fixed-delay-ms:30000}")
    fun publishPending() {
        paymentOutboxService.publishPending()
    }
}
