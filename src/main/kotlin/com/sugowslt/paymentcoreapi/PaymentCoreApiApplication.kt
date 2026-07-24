package com.sugowslt.paymentcoreapi

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan

@SpringBootApplication
@ConfigurationPropertiesScan
class PaymentCoreApiApplication

fun main(args: Array<String>) {
    runApplication<PaymentCoreApiApplication>(*args)
}
