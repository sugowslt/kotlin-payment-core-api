package com.sugowslt.paymentcoreapi.exception

class OutboxEventNotFoundException(eventId: Long) : RuntimeException("outbox event not found: $eventId")
