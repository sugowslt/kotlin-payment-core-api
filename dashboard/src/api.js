async function requestJson(url, options = {}, failureLabel = '요청') {
  let response

  try {
    response = await fetch(url, options)
  } catch (error) {
    const networkError = new Error('백엔드 연결 실패: project1 API가 실행 중인지 확인하세요. (기본 주소: http://localhost:8080)')
    networkError.cause = error
    networkError.traceId = null
    throw networkError
  }

  const traceId = response.headers.get('X-Trace-Id')
  const contentType = response.headers.get('content-type') || ''

  let body = null
  let rawText = ''

  if (contentType.includes('application/json')) {
    body = await response.json().catch(() => null)
  } else {
    rawText = await response.text().catch(() => '')
  }

  if (!response.ok) {
    const textMessage = rawText.trim().replace(/\s+/g, ' ').slice(0, 160)
    const fallbackMessage = response.status >= 500
      ? `${failureLabel} 실패: ${response.status} (서버 오류 또는 백엔드 미실행)`
      : `${failureLabel} 실패: ${response.status}`

    const error = new Error(
      body?.message ||
      body?.error ||
      textMessage ||
      fallbackMessage,
    )
    error.traceId = body?.traceId || traceId
    error.status = response.status
    throw error
  }

  return {
    body,
    traceId,
  }
}

const localOperationsToken = import.meta.env.VITE_WEBHOOK_REPLAY_TOKEN || 'local-dashboard-replay-token'

function operationsHeaders() {
  return {
    'X-Webhook-Replay-Token': localOperationsToken,
  }
}

export async function getPaymentsCursor(cursorId, size = 20) {
  const query = new URLSearchParams({ size: String(size) })
  if (cursorId) {
    query.append('cursorId', String(cursorId))
  }

  return requestJson(`/api/v1/payments/cursor?${query.toString()}`, {}, '목록 조회')
}

export async function createPayment(payload) {
  return requestJson('/api/v1/payments', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(payload),
  }, '결제 생성')
}

export async function transitionPayment(paymentId, action) {
  return requestJson(`/api/v1/payments/${paymentId}/${action}`, {
    method: 'POST',
  }, action)
}

export async function approvePayment(paymentId, idempotencyKey) {
  return requestJson(`/api/v1/payments/${paymentId}/approve`, {
    method: 'POST',
    headers: {
      'Idempotency-Key': idempotencyKey,
    },
  }, '결제 승인')
}

export async function sendPaymentStatusWebhook(payload, transmissionId) {
  return requestJson('/api/v1/webhooks/toss/payments', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'tosspayments-webhook-transmission-id': transmissionId,
    },
    body: JSON.stringify(payload),
  }, '결제 웹훅 처리')
}

export async function getWebhookMetrics() {
  return requestJson('/api/v1/internal/webhooks/metrics', {
    headers: operationsHeaders(),
  }, '웹훅 지표 조회')
}

export async function getOutboxMetrics() {
  return requestJson('/api/v1/internal/outbox/metrics', {
    headers: operationsHeaders(),
  }, '아웃박스 지표 조회')
}

export async function publishOutbox() {
  return requestJson('/api/v1/internal/outbox/publish', {
    method: 'POST',
    headers: operationsHeaders(),
  }, '아웃박스 로컬 처리')
}
