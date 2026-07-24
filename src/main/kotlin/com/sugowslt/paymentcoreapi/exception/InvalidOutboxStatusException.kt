package com.sugowslt.paymentcoreapi.exception

import com.sugowslt.paymentcoreapi.entity.OutboxStatus

class InvalidOutboxStatusException(eventId: Long, status: OutboxStatus) :
    RuntimeException("outbox event $eventId cannot be retried from status $status")
