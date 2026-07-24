package com.sugowslt.paymentcoreapi.gateway

import com.sugowslt.paymentcoreapi.entity.PaymentMethod
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.util.Base64

class TossPaymentGatewayTest {

    @Test
    fun `approve sends Toss confirm request with basic auth and idempotency key`() {
        val builder = RestClient.builder().baseUrl("http://localhost")
        val server = MockRestServiceServer.bindTo(builder).build()
        val gateway = TossPaymentGateway(
            properties = TossPaymentGatewayProperties(
                baseUrl = "http://localhost",
                secretKey = "test_sk_example",
                maxRetries = 0,
            ),
            restClient = builder
                .defaultHeaders { headers -> headers.setBasicAuth("test_sk_example", "") }
                .build(),
        )

        server.expect(requestTo("http://localhost/v1/payments/confirm"))
            .andExpect(method(org.springframework.http.HttpMethod.POST))
            .andExpect(header(HttpHeaders.AUTHORIZATION, basicAuth("test_sk_example")))
            .andExpect(header("Idempotency-Key", "approval-key-1"))
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(content().json("""
                {
                  "paymentKey": "toss-payment-key-1",
                  "orderId": "101",
                  "amount": 1200.50
                }
            """.trimIndent()))
            .andRespond(
                withSuccess(
                    """
                    {
                      "paymentKey": "toss-payment-key-1",
                      "status": "DONE"
                    }
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON,
                ),
            )

        val result = gateway.approve(
            PaymentGatewayApprovalRequest(
                paymentId = 1,
                orderId = 101,
                amount = BigDecimal("1200.50"),
                method = PaymentMethod.CARD,
                approvalIdempotencyKey = "approval-key-1",
                paymentKey = "toss-payment-key-1",
            ),
        )

        assertEquals("toss-payment-key-1", result.providerTransactionId)
        server.verify()
    }

    @Test
    fun `approve retries one time on server error`() {
        val builder = RestClient.builder().baseUrl("http://localhost")
        val server = MockRestServiceServer.bindTo(builder).build()
        val gateway = TossPaymentGateway(
            properties = TossPaymentGatewayProperties(
                baseUrl = "http://localhost",
                secretKey = "test_sk_example",
                maxRetries = 1,
            ),
            restClient = builder.build(),
        )
        val request = PaymentGatewayApprovalRequest(
            paymentId = 2,
            orderId = 102,
            amount = BigDecimal("2000.00"),
            method = PaymentMethod.CARD,
            approvalIdempotencyKey = "approval-key-2",
            paymentKey = "toss-payment-key-2",
        )

        repeat(1) {
            server.expect(requestTo("http://localhost/v1/payments/confirm"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andRespond(withServerError())
        }
        server.expect(requestTo("http://localhost/v1/payments/confirm"))
            .andRespond(
                withSuccess(
                    "{\"paymentKey\":\"toss-payment-key-2\",\"status\":\"DONE\"}",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val result = gateway.approve(request)

        assertEquals("toss-payment-key-2", result.providerTransactionId)
        server.verify()
    }

    @Test
    fun `approve rejects before making a request when payment key is missing`() {
        val builder = RestClient.builder().baseUrl("http://localhost")
        val server = MockRestServiceServer.bindTo(builder).build()
        val gateway = TossPaymentGateway(
            properties = TossPaymentGatewayProperties(
                baseUrl = "http://localhost",
                secretKey = "test_sk_example",
            ),
            restClient = builder.build(),
        )

        assertThrows(PaymentGatewayRequestException::class.java) {
            gateway.approve(
                PaymentGatewayApprovalRequest(
                    paymentId = 3,
                    orderId = 103,
                    amount = BigDecimal("3000.00"),
                    method = PaymentMethod.CARD,
                    approvalIdempotencyKey = "approval-key-3",
                ),
            )
        }

        server.verify()
    }

    @Test
    fun `cancel sends Toss cancellation request with reason and idempotency key`() {
        val builder = RestClient.builder().baseUrl("http://localhost")
        val server = MockRestServiceServer.bindTo(builder).build()
        val gateway = TossPaymentGateway(
            properties = TossPaymentGatewayProperties(
                baseUrl = "http://localhost",
                secretKey = "test_sk_example",
                maxRetries = 0,
            ),
            restClient = builder
                .defaultHeaders { headers -> headers.setBasicAuth("test_sk_example", "") }
                .build(),
        )

        server.expect(requestTo("http://localhost/v1/payments/toss-payment-key-4/cancel"))
            .andExpect(method(org.springframework.http.HttpMethod.POST))
            .andExpect(header(HttpHeaders.AUTHORIZATION, basicAuth("test_sk_example")))
            .andExpect(header("Idempotency-Key", "cancel-key-4"))
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(content().json("""
                {
                  "cancelReason": "customer request"
                }
            """.trimIndent()))
            .andRespond(
                withSuccess(
                    """
                    {
                      "paymentKey": "toss-payment-key-4",
                      "status": "CANCELED",
                      "cancels": [{"transactionKey": "cancel-tx-4"}]
                    }
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON,
                ),
            )

        val result = gateway.cancel(
            PaymentGatewayCancellationRequest(
                paymentId = 4,
                providerTransactionId = "toss-payment-key-4",
                cancelReason = "customer request",
                cancellationIdempotencyKey = "cancel-key-4",
            ),
        )

        assertEquals("cancel-tx-4", result.providerCancellationId)
        server.verify()
    }

    @Test
    fun `cancel rejects before making a request when provider transaction id is missing`() {
        val builder = RestClient.builder().baseUrl("http://localhost")
        val server = MockRestServiceServer.bindTo(builder).build()
        val gateway = TossPaymentGateway(
            properties = TossPaymentGatewayProperties(
                baseUrl = "http://localhost",
                secretKey = "test_sk_example",
            ),
            restClient = builder.build(),
        )

        assertThrows(PaymentGatewayRequestException::class.java) {
            gateway.cancel(
                PaymentGatewayCancellationRequest(
                    paymentId = 5,
                    providerTransactionId = null,
                ),
            )
        }

        server.verify()
    }

    private fun basicAuth(secretKey: String): String {
        val encoded = Base64.getEncoder().encodeToString("$secretKey:".toByteArray(StandardCharsets.UTF_8))
        return "Basic $encoded"
    }
}
