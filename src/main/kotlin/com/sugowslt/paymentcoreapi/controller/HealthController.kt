package com.sugowslt.paymentcoreapi.controller

import com.sugowslt.paymentcoreapi.service.PaymentIdempotencyRedisProperties
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import javax.sql.DataSource

@RestController
@RequestMapping("/api/v1")
class HealthController(
    private val dataSource: DataSource,
    private val redisConnectionFactory: RedisConnectionFactory,
    private val redisProperties: PaymentIdempotencyRedisProperties,
) {

    @GetMapping("/health")
    fun health(): ResponseEntity<HealthResponse> {
        val databaseStatus = checkComponent {
            dataSource.connection.use { it.isValid(2) }
        }
        val redisStatus = if (redisProperties.enabled) {
            checkComponent {
                redisConnectionFactory.connection.use { it.ping() == "PONG" }
            }
        } else {
            "disabled"
        }
        val ready = databaseStatus == "up" && redisStatus in setOf("up", "disabled")
        val response = HealthResponse(
            status = if (ready) "ok" else "degraded",
            components = mapOf(
                "database" to databaseStatus,
                "redis" to redisStatus,
            ),
        )

        return ResponseEntity
            .status(if (ready) HttpStatus.OK else HttpStatus.SERVICE_UNAVAILABLE)
            .body(response)
    }

    private fun checkComponent(check: () -> Boolean): String = try {
        if (check()) "up" else "down"
    } catch (_: Exception) {
        "down"
    }
}

data class HealthResponse(
    val status: String,
    val components: Map<String, String>,
)
