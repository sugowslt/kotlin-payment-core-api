package com.sugowslt.paymentcoreapi.service

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.time.Duration
import java.util.UUID

sealed interface PaymentIdempotencyAcquireResult {
    data class Acquired(val lease: PaymentIdempotencyLease) : PaymentIdempotencyAcquireResult

    data object InProgress : PaymentIdempotencyAcquireResult

    data object Bypassed : PaymentIdempotencyAcquireResult
}

data class PaymentIdempotencyLease(
    val redisKey: String,
    val token: String,
)

interface PaymentIdempotencyGuard {
    fun tryAcquire(paymentId: Long, idempotencyKey: String): PaymentIdempotencyAcquireResult

    fun release(lease: PaymentIdempotencyLease)
}

@Component
@ConditionalOnProperty(
    prefix = "payment.idempotency.redis",
    name = ["enabled"],
    havingValue = "false",
    matchIfMissing = true,
)
class NoopPaymentIdempotencyGuard : PaymentIdempotencyGuard {
    override fun tryAcquire(paymentId: Long, idempotencyKey: String) = PaymentIdempotencyAcquireResult.Bypassed

    override fun release(lease: PaymentIdempotencyLease) = Unit
}

@ConfigurationProperties(prefix = "payment.idempotency.redis")
data class PaymentIdempotencyRedisProperties(
    val enabled: Boolean = false,
    val ttlSeconds: Long = 30,
)

@Component
@ConditionalOnProperty(
    prefix = "payment.idempotency.redis",
    name = ["enabled"],
    havingValue = "true",
)
class RedisPaymentIdempotencyGuard(
    private val redisTemplate: StringRedisTemplate,
    private val properties: PaymentIdempotencyRedisProperties,
) : PaymentIdempotencyGuard {

    private val logger = LoggerFactory.getLogger(javaClass)
    private val releaseScript = DefaultRedisScript<Long>(
        "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
        Long::class.java,
    )

    override fun tryAcquire(paymentId: Long, idempotencyKey: String): PaymentIdempotencyAcquireResult {
        val redisKey = RedisPaymentIdempotencyKey.approval(paymentId, idempotencyKey)
        val token = UUID.randomUUID().toString()

        return try {
            val acquired = redisTemplate.opsForValue().setIfAbsent(
                redisKey,
                token,
                Duration.ofSeconds(properties.ttlSeconds.coerceAtLeast(1)),
            ) == true

            if (acquired) {
                PaymentIdempotencyAcquireResult.Acquired(PaymentIdempotencyLease(redisKey, token))
            } else {
                PaymentIdempotencyAcquireResult.InProgress
            }
        } catch (ex: RuntimeException) {
            logger.warn("Redis idempotency guard unavailable; falling back to database lock", ex)
            PaymentIdempotencyAcquireResult.Bypassed
        }
    }

    override fun release(lease: PaymentIdempotencyLease) {
        try {
            redisTemplate.execute(releaseScript, listOf(lease.redisKey), lease.token)
        } catch (ex: RuntimeException) {
            logger.warn("Redis idempotency lease release failed; TTL will clean it up", ex)
        }
    }
}

object RedisPaymentIdempotencyKey {
    fun approval(paymentId: Long, idempotencyKey: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(idempotencyKey.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return "payment:idempotency:approval:$paymentId:$digest"
    }
}
