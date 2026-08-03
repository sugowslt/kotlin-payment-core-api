package com.sugowslt.paymentcoreapi

import com.sugowslt.paymentcoreapi.repository.PaymentRepository
import com.sugowslt.paymentcoreapi.repository.PaymentCancellationRepository
import com.sugowslt.paymentcoreapi.repository.InternalOperationAuditEventRepository
import com.sugowslt.paymentcoreapi.repository.PaymentOutboxEventRepository
import com.sugowslt.paymentcoreapi.repository.PaymentSettlementRepository
import com.sugowslt.paymentcoreapi.repository.PaymentWebhookEventRepository
import com.sugowslt.paymentcoreapi.entity.OutboxStatus
import org.hamcrest.Matchers
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.context.TestPropertySource
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
    properties = [
        "payment.webhook.replay-token=test-replay-token",
        "payment.idempotency.redis.enabled=false",
    ],
)
class PaymentControllerIntegrationTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val paymentRepository: PaymentRepository,
    @Autowired private val paymentCancellationRepository: PaymentCancellationRepository,
    @Autowired private val internalOperationAuditEventRepository: InternalOperationAuditEventRepository,
    @Autowired private val paymentOutboxEventRepository: PaymentOutboxEventRepository,
    @Autowired private val paymentSettlementRepository: PaymentSettlementRepository,
    @Autowired private val paymentWebhookEventRepository: PaymentWebhookEventRepository,
) {

    companion object {
        private const val TRACE_ID_HEADER = "X-Trace-Id"
    }

    @BeforeEach
    fun clearPayments() {
        paymentRepository.deleteAll()
        paymentCancellationRepository.deleteAll()
        internalOperationAuditEventRepository.deleteAll()
        paymentOutboxEventRepository.deleteAll()
        paymentSettlementRepository.deleteAll()
        paymentWebhookEventRepository.deleteAll()
    }

    @Test
    fun `health reports database and disabled redis status`() {
        mockMvc.perform(get("/api/v1/health"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("ok"))
            .andExpect(jsonPath("$.components.database").value("up"))
            .andExpect(jsonPath("$.components.redis").value("disabled"))
    }

    @Test
    fun `openapi exposes payment lifecycle contract`() {
        mockMvc.perform(get("/api-docs"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.info.title").value("Kotlin Payment Core API"))
            .andExpect(jsonPath("$.info.version").value("v1"))
            .andExpect(jsonPath("$.paths['/api/v1/payments']").exists())
            .andExpect(jsonPath("$.paths['/api/v1/payments/{paymentId}/approve'].post.summary").value("결제 승인"))
            .andExpect(jsonPath("$.paths['/api/v1/internal/outbox/{eventId}/retry'].post.summary").value("실패 아웃박스 재시도"))
    }

    @Test
    fun `get payment returns 200 when payment exists`() {
        val requestBody =
            """
            {
              "orderId": 5001,
              "idempotencyKey": "lookup-5001",
              "amount": 999.99,
              "method": "CARD"
            }
            """.trimIndent()

        val createResponse = mockMvc.perform(
            post("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody),
        )
            .andExpect(status().isCreated)
            .andReturn()

        val createdJson = createResponse.response.contentAsString
        val idRegex = "\"id\":(\\d+)".toRegex()
        val createdId = idRegex.find(createdJson)?.groupValues?.get(1)
            ?: throw IllegalStateException("cannot parse created payment id")

        mockMvc.perform(get("/api/v1/payments/$createdId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(createdId.toLong()))
            .andExpect(jsonPath("$.orderId").value(5001))
            .andExpect(jsonPath("$.status").value("PENDING"))
    }

    @Test
    fun `success response keeps trace id when request header is provided`() {
        val traceId = "trace-success-123"

        mockMvc.perform(
            get("/api/v1/payments")
                .header("X-Trace-Id", traceId),
        )
            .andExpect(status().isOk)
            .andExpect { result ->
                kotlin.test.assertEquals(traceId, result.response.getHeader("X-Trace-Id"))
            }
    }

    @Test
    fun `success response returns generated trace id when request header is missing`() {
        mockMvc.perform(get("/api/v1/payments"))
            .andExpect(status().isOk)
            .andExpect { result ->
                val generatedTraceId = result.response.getHeader("X-Trace-Id")
                kotlin.test.assertTrue(!generatedTraceId.isNullOrBlank())
            }
    }

    @Test
    fun `get payment returns 404 when payment does not exist`() {
        mockMvc.perform(get("/api/v1/payments/999999"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"))
    }

    @Test
    fun `error response keeps trace id when request header is provided`() {
        val traceId = "trace-fixed-123"

        mockMvc.perform(
            get("/api/v1/payments/999999")
                .header(TRACE_ID_HEADER, traceId),
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.traceId").value(traceId))
            .andExpect { result ->
                kotlin.test.assertEquals(traceId, result.response.getHeader(TRACE_ID_HEADER))
            }
    }

    @Test
    fun `error response includes generated trace id when request header is missing`() {
        val requestBody =
            """
            {
              "orderId": 0,
              "idempotencyKey": "",
              "amount": 0,
              "method": "CARD"
            }
            """.trimIndent()

        val result = mockMvc.perform(
            post("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.traceId").isString)
            .andReturn()

        val responseTraceId = result.response.getHeader(TRACE_ID_HEADER)
        val bodyTraceIdRegex = "\"traceId\":\"([^\"]+)\"".toRegex()
        val bodyTraceId = bodyTraceIdRegex.find(result.response.contentAsString)?.groupValues?.get(1)

        kotlin.test.assertTrue(!responseTraceId.isNullOrBlank())
        kotlin.test.assertEquals(responseTraceId, bodyTraceId)
    }

    @Test
    fun `create payment returns 201`() {
        val requestBody =
            """
            {
              "orderId": 1001,
              "idempotencyKey": "key-1001",
              "amount": 15000.50,
              "method": "CARD"
            }
            """.trimIndent()

        mockMvc.perform(
            post("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.orderId").value(1001))
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.method").value("CARD"))
    }

    @Test
    fun `create payment returns 400 when validation fails`() {
        val requestBody =
            """
            {
              "orderId": 0,
              "idempotencyKey": "",
              "amount": 0,
              "method": "CARD"
            }
            """.trimIndent()

        mockMvc.perform(
            post("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.details", Matchers.hasSize<Int>(3)))
    }

    @Test
    fun `get payments returns paginated results with default parameters`() {
        // Create 5 payments
        repeat(5) { i ->
            val requestBody =
                """
                {
                  "orderId": ${6000 + i},
                  "idempotencyKey": "paginated-key-$i",
                  "amount": 1000.00,
                  "method": "CARD"
                }
                """.trimIndent()

            mockMvc.perform(
                post("/api/v1/payments")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody),
            )
                .andExpect(status().isCreated)
        }

        mockMvc.perform(get("/api/v1/payments"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content", Matchers.hasSize<Int>(5)))
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(20))
            .andExpect(jsonPath("$.totalElements").value(5))
    }

    @Test
    fun `get payments returns correct sorting by created_at descending`() {
        // Create 3 payments with different times
        val payment1RequestBody =
            """
            {
              "orderId": 7001,
              "idempotencyKey": "sort-key-1",
              "amount": 1000.00,
              "method": "CARD"
            }
            """.trimIndent()

        val payment2RequestBody =
            """
            {
              "orderId": 7002,
              "idempotencyKey": "sort-key-2",
              "amount": 2000.00,
              "method": "CARD"
            }
            """.trimIndent()

        val payment3RequestBody =
            """
            {
              "orderId": 7003,
              "idempotencyKey": "sort-key-3",
              "amount": 3000.00,
              "method": "CARD"
            }
            """.trimIndent()

        mockMvc.perform(
            post("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payment1RequestBody),
        )
            .andExpect(status().isCreated)

        mockMvc.perform(
            post("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payment2RequestBody),
        )
            .andExpect(status().isCreated)

        mockMvc.perform(
            post("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payment3RequestBody),
        )
            .andExpect(status().isCreated)

        mockMvc.perform(get("/api/v1/payments?page=0&size=10"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content", Matchers.hasSize<Int>(3)))
            .andExpect(jsonPath("$.content[0].amount").value(3000.00))
            .andExpect(jsonPath("$.content[1].amount").value(2000.00))
            .andExpect(jsonPath("$.content[2].amount").value(1000.00))
    }

    @Test
    fun `get payments cursor returns next cursor and next page without overlap`() {
        repeat(5) { i ->
            val requestBody =
                """
                {
                  "orderId": ${9000 + i},
                  "idempotencyKey": "cursor-key-$i",
                  "amount": 1000.00,
                  "method": "CARD"
                }
                """.trimIndent()

            mockMvc.perform(
                post("/api/v1/payments")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody),
            )
                .andExpect(status().isCreated)
        }

        val firstPageResult = mockMvc.perform(get("/api/v1/payments/cursor?size=2"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content", Matchers.hasSize<Int>(2)))
            .andExpect(jsonPath("$.hasNext").value(true))
            .andExpect(jsonPath("$.nextCursorId").isNumber)
            .andReturn()

        val firstResponseJson = firstPageResult.response.contentAsString
        val cursorRegex = "\"nextCursorId\":(\\d+)".toRegex()
        val nextCursorId = cursorRegex.find(firstResponseJson)?.groupValues?.get(1)
            ?: throw IllegalStateException("cannot parse nextCursorId")

        mockMvc.perform(get("/api/v1/payments/cursor?size=2&cursorId=$nextCursorId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content", Matchers.hasSize<Int>(2)))
            .andExpect(jsonPath("$.content[0].id", Matchers.lessThan(nextCursorId.toInt())))
    }

    @Test
    fun `approve payment returns 200 when status is pending`() {
        val requestBody =
            """
            {
              "orderId": 8001,
              "idempotencyKey": "approve-key-1",
              "amount": 5000.00,
              "method": "CARD"
            }
            """.trimIndent()

        val createResponse = mockMvc.perform(
            post("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody),
        )
            .andExpect(status().isCreated)
            .andReturn()

        val createdJson = createResponse.response.contentAsString
        val idRegex = "\"id\":(\\d+)".toRegex()
        val createdId = idRegex.find(createdJson)?.groupValues?.get(1)
            ?: throw IllegalStateException("cannot parse created payment id")

        mockMvc.perform(post("/api/v1/payments/$createdId/approve").header("Idempotency-Key", "approve-request-1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(createdId.toLong()))
            .andExpect(jsonPath("$.status").value("APPROVED"))
    }

    @Test
    fun `cancel payment returns 200 when status is approved`() {
        val requestBody =
            """
            {
              "orderId": 8002,
              "idempotencyKey": "cancel-key-1",
              "amount": 7000.00,
              "method": "BANK_TRANSFER"
            }
            """.trimIndent()

        val createResponse = mockMvc.perform(
            post("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody),
        )
            .andExpect(status().isCreated)
            .andReturn()

        val createdJson = createResponse.response.contentAsString
        val idRegex = "\"id\":(\\d+)".toRegex()
        val createdId = idRegex.find(createdJson)?.groupValues?.get(1)
            ?: throw IllegalStateException("cannot parse created payment id")

        mockMvc.perform(post("/api/v1/payments/$createdId/approve").header("Idempotency-Key", "cancel-approve-1"))
            .andExpect(status().isOk)

        mockMvc.perform(
            post("/api/v1/payments/$createdId/cancel")
                .header("Idempotency-Key", "cancel-key-1"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(createdId.toLong()))
            .andExpect(jsonPath("$.status").value("CANCELED"))

        mockMvc.perform(
            post("/api/v1/payments/$createdId/cancel")
                .header("Idempotency-Key", "cancel-key-1"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("CANCELED"))

        mockMvc.perform(
            post("/api/v1/payments/$createdId/cancel")
                .header("Idempotency-Key", "different-cancel-key-1"),
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("INVALID_PAYMENT_STATUS_TRANSITION"))
    }

    @Test
    fun `cancel payment returns 409 when status is pending`() {
        val requestBody =
            """
            {
              "orderId": 8003,
              "idempotencyKey": "cancel-invalid-key",
              "amount": 9000.00,
              "method": "CARD"
            }
            """.trimIndent()

        val createResponse = mockMvc.perform(
            post("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody),
        )
            .andExpect(status().isCreated)
            .andReturn()

        val createdJson = createResponse.response.contentAsString
        val idRegex = "\"id\":(\\d+)".toRegex()
        val createdId = idRegex.find(createdJson)?.groupValues?.get(1)
            ?: throw IllegalStateException("cannot parse created payment id")

        mockMvc.perform(
            post("/api/v1/payments/$createdId/cancel")
                .header("Idempotency-Key", "cancel-invalid-key"),
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("INVALID_PAYMENT_STATUS_TRANSITION"))
    }

    @Test
    fun `partial cancellations accumulate and full cancellation changes status`() {
        val createResponse = mockMvc.perform(
            post("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "orderId": 8010,
                      "idempotencyKey": "partial-cancel-8010",
                      "amount": 10000.00,
                      "method": "CARD"
                    }
                    """.trimIndent(),
                ),
        ).andExpect(status().isCreated).andReturn()

        val createdId = "\"id\":(\\d+)".toRegex()
            .find(createResponse.response.contentAsString)?.groupValues?.get(1)
            ?: throw IllegalStateException("cannot parse created payment id")

        mockMvc.perform(post("/api/v1/payments/$createdId/approve").header("Idempotency-Key", "partial-approve-8010"))
            .andExpect(status().isOk)

        mockMvc.perform(
            post("/api/v1/payments/$createdId/cancel")
                .header("Idempotency-Key", "partial-cancel-key-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"cancelReason":"partial customer request","cancelAmount":3000.00}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("APPROVED"))
            .andExpect(jsonPath("$.canceledAmount").value(3000.00))

        mockMvc.perform(
            post("/api/v1/payments/$createdId/cancel")
                .header("Idempotency-Key", "partial-cancel-key-2")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"cancelReason":"remaining customer request","cancelAmount":7000.00}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("CANCELED"))
            .andExpect(jsonPath("$.canceledAmount").value(10000.00))
    }

    @Test
    fun `cancellation amount greater than remaining amount returns 400`() {
        val createResponse = mockMvc.perform(
            post("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "orderId": 8011,
                      "idempotencyKey": "partial-cancel-invalid-8011",
                      "amount": 10000.00,
                      "method": "CARD"
                    }
                    """.trimIndent(),
                ),
        ).andExpect(status().isCreated).andReturn()

        val createdId = "\"id\":(\\d+)".toRegex()
            .find(createResponse.response.contentAsString)?.groupValues?.get(1)
            ?: throw IllegalStateException("cannot parse created payment id")

        mockMvc.perform(post("/api/v1/payments/$createdId/approve").header("Idempotency-Key", "partial-approve-8011"))
            .andExpect(status().isOk)

        mockMvc.perform(
            post("/api/v1/payments/$createdId/cancel")
                .header("Idempotency-Key", "partial-cancel-invalid-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"cancelReason":"invalid amount","cancelAmount":10000.01}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_PAYMENT_CANCELLATION"))
    }

    @Test
    fun `approve payment returns 409 when status is canceled`() {
        val requestBody =
            """
            {
              "orderId": 8004,
              "idempotencyKey": "approve-invalid-key",
              "amount": 11000.00,
              "method": "CARD"
            }
            """.trimIndent()

        val createResponse = mockMvc.perform(
            post("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody),
        )
            .andExpect(status().isCreated)
            .andReturn()

        val createdJson = createResponse.response.contentAsString
        val idRegex = "\"id\":(\\d+)".toRegex()
        val createdId = idRegex.find(createdJson)?.groupValues?.get(1)
            ?: throw IllegalStateException("cannot parse created payment id")

        mockMvc.perform(post("/api/v1/payments/$createdId/approve").header("Idempotency-Key", "approve-invalid-1"))
            .andExpect(status().isOk)
        mockMvc.perform(
            post("/api/v1/payments/$createdId/cancel")
                .header("Idempotency-Key", "cancel-approve-key-1"),
        )
            .andExpect(status().isOk)

        mockMvc.perform(post("/api/v1/payments/$createdId/approve").header("Idempotency-Key", "approve-invalid-2"))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("INVALID_PAYMENT_STATUS_TRANSITION"))
    }

    @Test
    fun `repeating approve request with same idempotency key returns the same approved payment`() {
        val requestBody =
            """
            {
              "orderId": 8010,
              "idempotencyKey": "approve-retry-payment",
              "amount": 12000.00,
              "method": "CARD"
            }
            """.trimIndent()

        val createResponse = mockMvc.perform(
            post("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody),
        ).andExpect(status().isCreated).andReturn()

        val createdId = "\"id\":(\\d+)".toRegex()
            .find(createResponse.response.contentAsString)?.groupValues?.get(1)
            ?: throw IllegalStateException("cannot parse created payment id")

        val approvalKey = "approve-retry-key"
        mockMvc.perform(
            post("/api/v1/payments/$createdId/approve")
                .header("Idempotency-Key", approvalKey),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("APPROVED"))

        mockMvc.perform(
            post("/api/v1/payments/$createdId/approve")
                .header("Idempotency-Key", approvalKey),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(createdId.toLong()))
            .andExpect(jsonPath("$.status").value("APPROVED"))
    }

    @Test
    fun `approval creates settlement snapshot and settlement requested outbox event`() {
        val createResponse = mockMvc.perform(
            post("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "orderId": 8012,
                      "idempotencyKey": "settlement-payment-8012",
                      "amount": 1000.00,
                      "method": "CARD"
                    }
                    """.trimIndent(),
                ),
        ).andExpect(status().isCreated).andReturn()

        val createdId = "\"id\":(\\d+)".toRegex()
            .find(createResponse.response.contentAsString)?.groupValues?.get(1)
            ?: throw IllegalStateException("cannot parse created payment id")

        mockMvc.perform(
            post("/api/v1/payments/$createdId/approve")
                .header("Idempotency-Key", "settlement-approve-8012"),
        ).andExpect(status().isOk)

        val settlement = paymentSettlementRepository.findByPaymentId(createdId.toLong())
            ?: throw IllegalStateException("settlement snapshot was not created")
        assertEquals("1000.00", settlement.grossAmount.toPlainString())
        assertEquals(300, settlement.feeRateBps)
        assertEquals("30.00", settlement.feeAmount.toPlainString())
        assertEquals("970.00", settlement.settlementAmount.toPlainString())
        assertEquals("REQUESTED", settlement.status.name)

        val eventTypes = paymentOutboxEventRepository.findAll()
            .filter { it.aggregateId == createdId.toLong() }
            .map { it.eventType }
        assertEquals(listOf("PAYMENT_CREATED", "PAYMENT_APPROVED", "SETTLEMENT_REQUESTED"), eventTypes)
    }

    @Test
    fun `concurrent approve requests with different keys allow only one approval`() {
        val requestBody =
            """
            {
              "orderId": 8011,
              "idempotencyKey": "approve-concurrent-payment",
              "amount": 13000.00,
              "method": "CARD"
            }
            """.trimIndent()

        val createResponse = mockMvc.perform(
            post("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody),
        ).andExpect(status().isCreated).andReturn()

        val createdId = "\"id\":(\\d+)".toRegex()
            .find(createResponse.response.contentAsString)?.groupValues?.get(1)
            ?: throw IllegalStateException("cannot parse created payment id")

        val startGate = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val futures = listOf("concurrent-key-a", "concurrent-key-b").map { approvalKey ->
                executor.submit<Int> {
                    startGate.await()
                    mockMvc.perform(
                        post("/api/v1/payments/$createdId/approve")
                            .header("Idempotency-Key", approvalKey),
                    ).andReturn().response.status
                }
            }

            startGate.countDown()
            val statuses = futures.map { it.get(15, TimeUnit.SECONDS) }.sorted()

            assertEquals(listOf(200, 409), statuses)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `Toss payment status webhook approves pending payment and ignores duplicate transmission`() {
        val requestBody =
            """
            {
              "orderId": 8101,
              "idempotencyKey": "webhook-payment-8101",
              "amount": 15000.00,
              "method": "CARD"
            }
            """.trimIndent()

        val createResponse = mockMvc.perform(
            post("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody),
        ).andExpect(status().isCreated).andReturn()

        val createdId = "\"id\":(\\d+)".toRegex()
            .find(createResponse.response.contentAsString)?.groupValues?.get(1)
            ?: throw IllegalStateException("cannot parse created payment id")

        val webhookBody =
            """
            {
              "eventType": "PAYMENT_STATUS_CHANGED",
              "createdAt": "2026-07-24T18:00:00.000000",
              "data": {
                "paymentKey": "toss-payment-key-8101",
                "orderId": "8101",
                "status": "DONE"
              }
            }
            """.trimIndent()

        mockMvc.perform(
            post("/api/v1/webhooks/toss/payments")
                .header("tosspayments-webhook-transmission-id", "transmission-8101"),
        ).andExpect(status().isBadRequest)

        mockMvc.perform(
            post("/api/v1/webhooks/toss/payments")
                .header("tosspayments-webhook-transmission-id", "transmission-8101")
                .contentType(MediaType.APPLICATION_JSON)
                .content(webhookBody),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.result").value("PROCESSED_APPROVED"))

        mockMvc.perform(get("/api/v1/payments/$createdId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("APPROVED"))

        mockMvc.perform(
            post("/api/v1/webhooks/toss/payments")
                .header("tosspayments-webhook-transmission-id", "transmission-8101")
                .contentType(MediaType.APPLICATION_JSON)
                .content(webhookBody),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.result").value("DUPLICATE"))
    }

    @Test
    fun `Toss expired payment status webhook marks pending payment as failed`() {
        val requestBody =
            """
            {
              "orderId": 8102,
              "idempotencyKey": "webhook-payment-8102",
              "amount": 16000.00,
              "method": "CARD"
            }
            """.trimIndent()

        val createResponse = mockMvc.perform(
            post("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody),
        ).andExpect(status().isCreated).andReturn()

        val createdId = "\"id\":(\\d+)".toRegex()
            .find(createResponse.response.contentAsString)?.groupValues?.get(1)
            ?: throw IllegalStateException("cannot parse created payment id")

        val webhookBody =
            """
            {
              "eventType": "PAYMENT_STATUS_CHANGED",
              "createdAt": "2026-07-24T18:01:00.000000",
              "data": {
                "paymentKey": "toss-payment-key-8102",
                "orderId": "8102",
                "status": "EXPIRED"
              }
            }
            """.trimIndent()

        mockMvc.perform(
            post("/api/v1/webhooks/toss/payments")
                .header("tosspayments-webhook-transmission-id", "transmission-8102")
                .contentType(MediaType.APPLICATION_JSON)
                .content(webhookBody),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.result").value("PROCESSED_FAILED"))

        mockMvc.perform(get("/api/v1/payments/$createdId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("FAILED"))
    }

    @Test
    fun `Toss webhook returns 400 when transmission id is missing`() {
        val webhookBody =
            """
            {
              "eventType": "PAYMENT_STATUS_CHANGED",
              "createdAt": "2026-07-24T18:02:00.000000",
              "data": {
                "orderId": "8103",
                "status": "DONE"
              }
            }
            """.trimIndent()

        mockMvc.perform(
            post("/api/v1/webhooks/toss/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(webhookBody),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_WEBHOOK"))
    }

    @Test
    fun `protected webhook replay reprocesses an ignored event after payment is created`() {
        val transmissionId = "replay-transmission-8110"
        val webhookBody =
            """
            {
              "eventType": "PAYMENT_STATUS_CHANGED",
              "createdAt": "2026-07-24T18:03:00.000000",
              "data": {
                "paymentKey": "toss-payment-key-8110",
                "orderId": "8110",
                "status": "DONE"
              }
            }
            """.trimIndent()

        mockMvc.perform(
            post("/api/v1/webhooks/toss/payments")
                .header("tosspayments-webhook-transmission-id", transmissionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(webhookBody),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.result").value("IGNORED"))

        val createResponse = mockMvc.perform(
            post("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "orderId": 8110,
                      "idempotencyKey": "replay-payment-8110",
                      "amount": 17000.00,
                      "method": "CARD"
                    }
                    """.trimIndent(),
                ),
        ).andExpect(status().isCreated).andReturn()

        val createdId = "\"id\":(\\d+)".toRegex()
            .find(createResponse.response.contentAsString)?.groupValues?.get(1)
            ?: throw IllegalStateException("cannot parse created payment id")

        mockMvc.perform(
            post("/api/v1/internal/webhooks/$transmissionId/replay")
                .header("X-Webhook-Replay-Token", "test-replay-token"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.result").value("REPROCESSED_APPROVED"))

        mockMvc.perform(get("/api/v1/payments/$createdId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("APPROVED"))

        mockMvc.perform(
            get("/api/v1/internal/webhooks/metrics")
                .header("X-Webhook-Replay-Token", "test-replay-token"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalEvents").value(1))
            .andExpect(jsonPath("$.reprocessedEvents").value(1))
    }

    @Test
    fun `webhook metrics returns 403 without replay token`() {
        mockMvc.perform(get("/api/v1/internal/webhooks/metrics"))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("WEBHOOK_REPLAY_FORBIDDEN"))
    }

    @Test
    fun `internal audit events require replay token`() {
        mockMvc.perform(get("/api/v1/internal/audit-events"))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("WEBHOOK_REPLAY_FORBIDDEN"))
    }

    @Test
    fun `outbox metrics shows pending event and local publish marks it published`() {
        mockMvc.perform(
            post("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "orderId": 8111,
                      "idempotencyKey": "outbox-payment-8111",
                      "amount": 18000.00,
                      "method": "CARD"
                    }
                    """.trimIndent(),
                ),
        ).andExpect(status().isCreated)

        mockMvc.perform(
            get("/api/v1/internal/outbox/metrics")
                .header("X-Webhook-Replay-Token", "test-replay-token"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.pendingEvents").value(1))
            .andExpect(jsonPath("$.retryingEvents").value(0))
            .andExpect(jsonPath("$.publishedEvents").value(0))

        mockMvc.perform(
            post("/api/v1/internal/outbox/publish")
                .header("X-Webhook-Replay-Token", "test-replay-token"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.pendingEvents").value(0))
            .andExpect(jsonPath("$.retryingEvents").value(0))
            .andExpect(jsonPath("$.publishedEvents").value(1))

        mockMvc.perform(
            get("/api/v1/internal/outbox/metrics")
                .header("X-Webhook-Replay-Token", "test-replay-token"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.pendingEvents").value(0))
            .andExpect(jsonPath("$.publishedEvents").value(1))

        mockMvc.perform(
            get("/api/v1/internal/audit-events")
                .header("X-Webhook-Replay-Token", "test-replay-token"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].operation").value("OUTBOX_PUBLISH"))
            .andExpect(jsonPath("$[0].outcome").value("SUCCESS"))
    }

    @Test
    fun `internal operation audit stores trace id for webhook replay`() {
        val transmissionId = "audit-replay-transmission-8115"
        val webhookBody =
            """
            {
              "eventType": "PAYMENT_STATUS_CHANGED",
              "createdAt": "2026-07-24T18:05:00.000000",
              "data": {
                "paymentKey": "toss-payment-key-8115",
                "orderId": "8115",
                "status": "DONE"
              }
            }
            """.trimIndent()

        mockMvc.perform(
            post("/api/v1/webhooks/toss/payments")
                .header("tosspayments-webhook-transmission-id", transmissionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(webhookBody),
        ).andExpect(status().isOk)

        mockMvc.perform(
            post("/api/v1/internal/webhooks/$transmissionId/replay")
                .header("X-Webhook-Replay-Token", "test-replay-token")
                .header(TRACE_ID_HEADER, "audit-trace-8115"),
        ).andExpect(status().isOk)

        mockMvc.perform(
            get("/api/v1/internal/audit-events")
                .header("X-Webhook-Replay-Token", "test-replay-token"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].operation").value("WEBHOOK_REPLAY"))
            .andExpect(jsonPath("$[0].targetId").value(transmissionId))
            .andExpect(jsonPath("$[0].traceId").value("audit-trace-8115"))
    }

    @Test
    fun `concurrent outbox publish processes the same event once`() {
        mockMvc.perform(
            post("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "orderId": 8114,
                      "idempotencyKey": "outbox-concurrent-8114",
                      "amount": 21000.00,
                      "method": "CARD"
                    }
                    """.trimIndent(),
                ),
        ).andExpect(status().isCreated)

        val executor = Executors.newFixedThreadPool(2)
        val futures = (1..2).map {
            executor.submit<Int> {
                mockMvc.perform(
                    post("/api/v1/internal/outbox/publish")
                        .header("X-Webhook-Replay-Token", "test-replay-token"),
                ).andReturn().response.status
            }
        }

        val statuses = futures.map { it.get(10, TimeUnit.SECONDS) }
        executor.shutdown()
        executor.awaitTermination(10, TimeUnit.SECONDS)

        assertEquals(listOf(200, 200), statuses.sorted())
        mockMvc.perform(
            get("/api/v1/internal/outbox/metrics")
                .header("X-Webhook-Replay-Token", "test-replay-token"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.pendingEvents").value(0))
            .andExpect(jsonPath("$.publishedEvents").value(1))
    }

    @Test
    fun `failed outbox event can be manually requeued with replay token`() {
        mockMvc.perform(
            post("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "orderId": 8112,
                      "idempotencyKey": "outbox-retry-8112",
                      "amount": 19000.00,
                      "method": "CARD"
                    }
                    """.trimIndent(),
                ),
        ).andExpect(status().isCreated)

        val event = paymentOutboxEventRepository.findAll().single()
        event.status = OutboxStatus.FAILED
        event.retryCount = 3
        event.lastError = "broker unavailable"
        event.nextAttemptAt = null
        paymentOutboxEventRepository.save(event)

        mockMvc.perform(
            post("/api/v1/internal/outbox/${event.id}/retry")
                .header("X-Webhook-Replay-Token", "test-replay-token"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.eventId").value(event.id))
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.retryCount").value(0))

        assertEquals(OutboxStatus.PENDING, paymentOutboxEventRepository.findById(event.id).orElseThrow().status)
    }

    @Test
    fun `outbox retry rejects non failed event`() {
        mockMvc.perform(
            post("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "orderId": 8113,
                      "idempotencyKey": "outbox-retry-8113",
                      "amount": 20000.00,
                      "method": "CARD"
                    }
                    """.trimIndent(),
                ),
        ).andExpect(status().isCreated)

        val event = paymentOutboxEventRepository.findAll().single()

        mockMvc.perform(
            post("/api/v1/internal/outbox/${event.id}/retry")
                .header("X-Webhook-Replay-Token", "test-replay-token"),
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("INVALID_OUTBOX_STATUS"))
    }

    @Test
    fun `outbox retry requires replay token`() {
        mockMvc.perform(post("/api/v1/internal/outbox/999999/retry"))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("WEBHOOK_REPLAY_FORBIDDEN"))
    }

    @Test
    fun `openapi docs endpoint returns 200`() {
        mockMvc.perform(get("/api-docs"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.openapi").exists())
    }
}
