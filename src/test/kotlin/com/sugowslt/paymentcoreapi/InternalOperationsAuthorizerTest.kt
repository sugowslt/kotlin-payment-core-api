package com.sugowslt.paymentcoreapi

import com.sugowslt.paymentcoreapi.exception.WebhookReplayAccessDeniedException
import com.sugowslt.paymentcoreapi.gateway.WebhookOperationsProperties
import com.sugowslt.paymentcoreapi.security.InternalOperationsAuthorizer
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class InternalOperationsAuthorizerTest {

    @Test
    fun `authorizes the configured token`() {
        val authorizer = InternalOperationsAuthorizer(WebhookOperationsProperties("secret-token"))

        assertDoesNotThrow { authorizer.authorize("secret-token") }
    }

    @Test
    fun `rejects missing and mismatched tokens`() {
        val authorizer = InternalOperationsAuthorizer(WebhookOperationsProperties("secret-token"))

        assertThrows(WebhookReplayAccessDeniedException::class.java) { authorizer.authorize(null) }
        assertThrows(WebhookReplayAccessDeniedException::class.java) { authorizer.authorize("wrong-token") }
    }

    @Test
    fun `rejects tokens longer than configured limit`() {
        val authorizer = InternalOperationsAuthorizer(
            WebhookOperationsProperties("secret-token", maxTokenLength = 8),
        )

        assertThrows(WebhookReplayAccessDeniedException::class.java) {
            authorizer.authorize("secret-token")
        }
    }
}
