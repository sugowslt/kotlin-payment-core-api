package com.sugowslt.paymentcoreapi

import com.sugowslt.paymentcoreapi.repository.PaymentRepository
import org.hamcrest.Matchers
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
class PaymentControllerIntegrationTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val paymentRepository: PaymentRepository,
) {

    companion object {
        private const val TRACE_ID_HEADER = "X-Trace-Id"
    }

    @BeforeEach
    fun clearPayments() {
        paymentRepository.deleteAll()
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

        mockMvc.perform(post("/api/v1/payments/$createdId/cancel"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(createdId.toLong()))
            .andExpect(jsonPath("$.status").value("CANCELED"))
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

        mockMvc.perform(post("/api/v1/payments/$createdId/cancel"))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("INVALID_PAYMENT_STATUS_TRANSITION"))
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
        mockMvc.perform(post("/api/v1/payments/$createdId/cancel"))
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
    fun `openapi docs endpoint returns 200`() {
        mockMvc.perform(get("/api-docs"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.openapi").exists())
    }
}
