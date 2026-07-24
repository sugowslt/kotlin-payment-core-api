package com.sugowslt.paymentcoreapi.gateway

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = ["payment.gateway.provider"], havingValue = "toss")
class TossPaymentGatewayConfiguration {

    @Bean
    fun tossPaymentGateway(
        properties: TossPaymentGatewayProperties,
        restClientBuilder: RestClient.Builder,
    ): PaymentGateway {
        require(properties.secretKey.isNotBlank()) {
            "TOSS_PAYMENT_SECRET_KEY must not be blank when toss gateway is enabled"
        }

        val requestFactory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(properties.connectTimeoutMs)
            setReadTimeout(properties.readTimeoutMs)
        }
        val restClient = restClientBuilder
            .baseUrl(properties.baseUrl)
            .requestFactory(requestFactory)
            .defaultHeaders { headers -> headers.setBasicAuth(properties.secretKey, "") }
            .build()

        return TossPaymentGateway(properties, restClient)
    }
}
