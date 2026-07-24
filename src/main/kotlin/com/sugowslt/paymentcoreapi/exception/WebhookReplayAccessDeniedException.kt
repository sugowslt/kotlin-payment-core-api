package com.sugowslt.paymentcoreapi.exception

class WebhookReplayAccessDeniedException : RuntimeException("webhook replay token is invalid or disabled")
