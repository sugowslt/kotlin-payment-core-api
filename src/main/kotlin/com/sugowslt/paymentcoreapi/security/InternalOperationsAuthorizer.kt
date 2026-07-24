package com.sugowslt.paymentcoreapi.security

import com.sugowslt.paymentcoreapi.exception.WebhookReplayAccessDeniedException
import com.sugowslt.paymentcoreapi.gateway.WebhookOperationsProperties
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

@Component
class InternalOperationsAuthorizer(
    private val webhookOperationsProperties: WebhookOperationsProperties,
) {

    fun authorize(requestToken: String?) {
        val configuredToken = webhookOperationsProperties.replayToken
        if (configuredToken.isBlank() || requestToken.isNullOrBlank()) {
            throw WebhookReplayAccessDeniedException()
        }

        if (!MessageDigest.isEqual(
                configuredToken.toByteArray(StandardCharsets.UTF_8),
                requestToken.toByteArray(StandardCharsets.UTF_8),
            )
        ) {
            throw WebhookReplayAccessDeniedException()
        }
    }

    companion object {
        const val TOKEN_HEADER = "X-Webhook-Replay-Token"
    }
}
