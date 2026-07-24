package com.sugowslt.paymentcoreapi.service

import com.sugowslt.paymentcoreapi.entity.PaymentOutboxEvent
import org.springframework.stereotype.Component

interface PaymentOutboxPublisher {
    fun publish(event: PaymentOutboxEvent)
}

/**
 * 외부 브로커를 붙이기 전까지 사용하는 로컬 publisher입니다.
 * 이벤트의 발행 경계와 상태 전환을 검증할 수 있도록 별도 컴포넌트로 분리합니다.
 */
@Component
class LocalPaymentOutboxPublisher : PaymentOutboxPublisher {
    override fun publish(event: PaymentOutboxEvent) = Unit
}
