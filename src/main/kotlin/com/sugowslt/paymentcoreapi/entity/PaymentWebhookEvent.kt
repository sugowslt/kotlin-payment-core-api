package com.sugowslt.paymentcoreapi.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "payment_webhook_events")
class PaymentWebhookEvent(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "transmission_id", nullable = false, unique = true, length = 128)
    val transmissionId: String,

    @Column(name = "event_type", nullable = false, length = 80)
    val eventType: String,

    @Column(name = "order_id", length = 64)
    val orderId: String?,

    @Column(name = "provider_payment_key", length = 200)
    val providerPaymentKey: String?,

    @Column(name = "provider_status", length = 40)
    val providerStatus: String?,

    @jakarta.persistence.Lob
    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    val payload: String,

    @Column(name = "outcome", nullable = false, length = 60)
    var outcome: String = "RECEIVED",

    @Column(name = "received_at", nullable = false)
    val receivedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "processed_at")
    var processedAt: LocalDateTime? = null,
)
