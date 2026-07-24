package com.sugowslt.paymentcoreapi.gateway

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.http.MediaType
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException

class TossPaymentGateway(
    properties: TossPaymentGatewayProperties,
    private val restClient: RestClient,
) : PaymentGateway {

    private val maxRetries = properties.maxRetries.coerceIn(0, 3)

    override fun approve(request: PaymentGatewayApprovalRequest): PaymentGatewayApprovalResult {
        val paymentKey = request.paymentKey?.takeUnless { it.isBlank() }
            ?: throw PaymentGatewayRequestException("Payment-Key header is required for Toss gateway")

        var retryCount = 0
        while (true) {
            try {
                val response = restClient.post()
                    .uri("/v1/payments/confirm")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Idempotency-Key", request.approvalIdempotencyKey)
                    .body(
                        TossConfirmRequest(
                            paymentKey = paymentKey,
                            orderId = request.orderId.toString(),
                            amount = request.amount,
                        ),
                    )
                    .retrieve()
                    .body(TossConfirmResponse::class.java)
                    ?: throw PaymentGatewayUnavailableException("Toss gateway returned an empty response")

                if (response.status != "DONE") {
                    throw PaymentGatewayRejectedException(
                        "Toss gateway did not complete payment. status=${response.status}",
                    )
                }

                return PaymentGatewayApprovalResult(response.paymentKey)
            } catch (ex: PaymentGatewayRejectedException) {
                throw ex
            } catch (ex: PaymentGatewayRequestException) {
                throw ex
            } catch (ex: RestClientResponseException) {
                if (ex.statusCode.is4xxClientError) {
                    throw PaymentGatewayRejectedException("Toss gateway rejected payment: ${ex.statusCode.value()}")
                }
                if (retryCount >= maxRetries) {
                    throw PaymentGatewayUnavailableException(
                        "Toss gateway server error after retries: ${ex.statusCode.value()}",
                        ex,
                    )
                }
                retryCount++
            } catch (ex: ResourceAccessException) {
                if (retryCount >= maxRetries) {
                    throw PaymentGatewayUnavailableException("Toss gateway request timed out after retries", ex)
                }
                retryCount++
            }
        }
    }

    private data class TossConfirmRequest(
        val paymentKey: String,
        val orderId: String,
        val amount: java.math.BigDecimal,
    )

    private data class TossConfirmResponse(
        val paymentKey: String,
        val status: String,
        @JsonProperty("approvedAt") val approvedAt: String? = null,
    )
}
