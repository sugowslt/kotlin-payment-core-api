package com.sugowslt.paymentcoreapi

import com.sugowslt.paymentcoreapi.service.PaymentIdempotencyAcquireResult
import com.sugowslt.paymentcoreapi.service.PaymentIdempotencyRedisProperties
import com.sugowslt.paymentcoreapi.service.RedisPaymentIdempotencyGuard
import com.sugowslt.paymentcoreapi.service.RedisPaymentIdempotencyKey
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations

class PaymentIdempotencyGuardTest {

    @Test
    fun `approval redis key is stable and does not expose raw idempotency key`() {
        val first = RedisPaymentIdempotencyKey.approval(10L, "approval-key")
        val second = RedisPaymentIdempotencyKey.approval(10L, "approval-key")

        assertEquals(first, second)
        assertTrue(first.startsWith("payment:idempotency:approval:10:"))
        assertTrue("approval-key" !in first)
    }

    @Test
    fun `redis guard returns acquired when set if absent succeeds`() {
        val redisTemplate = mockk<StringRedisTemplate>()
        val valueOperations = mockk<ValueOperations<String, String>>()
        every { redisTemplate.opsForValue() } returns valueOperations
        every { valueOperations.setIfAbsent(any(), any(), any()) } returns true

        val guard = RedisPaymentIdempotencyGuard(
            redisTemplate,
            PaymentIdempotencyRedisProperties(enabled = true, ttlSeconds = 30),
        )

        val result = guard.tryAcquire(10L, "approval-key")

        assertTrue(result is PaymentIdempotencyAcquireResult.Acquired)
    }
}
