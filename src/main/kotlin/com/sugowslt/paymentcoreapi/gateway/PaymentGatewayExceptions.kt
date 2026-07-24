package com.sugowslt.paymentcoreapi.gateway

class PaymentGatewayRejectedException(message: String) : RuntimeException(message)

class PaymentGatewayUnavailableException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class PaymentGatewayRequestException(message: String) : RuntimeException(message)
