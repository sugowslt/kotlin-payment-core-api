<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import {
  approvePayment,
  cancelPayment,
  createPayment,
  getAuditEvents,
  getOutboxMetrics,
  getPaymentsCursor,
  getWebhookMetrics,
  publishOutbox,
  retryOutboxEvent,
  sendPaymentStatusWebhook,
  transitionPayment,
} from './api'

const activeView = ref('walkthrough')
const operations = ref({ webhook: null, outbox: null, audit: [] })
const operationsLoading = ref(false)
const operationsError = ref('')
const operationsMessage = ref('')
const failedOutboxEventId = ref('')

const projects = [
  {
    id: 'project1',
    badge: 'Project 1',
    title: 'Payment Core API',
    subtitle: '결제 도메인을 중심으로 상태 전이와 API 품질을 정리한 프로젝트',
    stage: '완료',
    highlight: 'cursor 기반 조회 최적화와 traceId 추적까지 포함한 결제 코어 API입니다.',
    stacks: ['Kotlin', 'Spring Boot', 'JPA', 'H2/MySQL', 'MockMvc'],
    stats: [
      { label: '구현 API', value: '6개' },
      { label: 'MySQL p95', value: '17.82ms' },
      { label: 'Vue Demo', value: '지원' },
    ],
    bullets: [
      '결제 생성/조회/승인/취소 상태 전이 API 구현',
      'OpenAPI/Swagger 문서화와 서비스/통합 테스트 정리',
      'offset 대비 keyset(cursor) 조회 전략 개선 근거 확보',
    ],
    evidence: [
      'H2/MySQL 반복 측정으로 목록 조회 전략 개선 근거를 남겼습니다.',
      'traceId 기반 운영 점검 시나리오와 장애 재현 플레이북까지 문서화했습니다.',
    ],
    colorClass: 'tone-blue',
  },
  {
    id: 'project2',
    badge: 'Project 2',
    title: 'Order Settlement Async',
    subtitle: '이벤트 기반 주문·정산 흐름과 확장성 실험을 담은 프로젝트',
    stage: '완료',
    highlight: '로컬 환경에서 1800 RPS까지 검증한 비동기 이벤트 처리 실험 프로젝트입니다.',
    stacks: ['Kotlin', 'Spring Boot', 'Kafka', 'Redis', 'MySQL', 'Docker'],
    stats: [
      { label: '최대 검증 RPS', value: '1800' },
      { label: '최대 p95', value: '3.47ms' },
      { label: '실험 조합', value: 'ACK×Partition 4개' },
    ],
    bullets: [
      '주문 이벤트 발행/소비 POC와 실패 로그 처리 구현',
      'Redis/Kafka/MySQL/Adminer/Kafka UI를 포함한 로컬 인프라 구성',
      '1~4차 성능 실측과 ACK/Partition 심화 실험까지 완료',
    ],
    evidence: [
      '모든 시나리오에서 Error 0.00%를 유지했습니다.',
      'README와 troubleshooting-log에 원인·해결·선택 이유를 체계적으로 정리했습니다.',
    ],
    colorClass: 'tone-violet',
  },
  {
    id: 'project3',
    badge: 'Project 3',
    title: 'Backend Observability Lab',
    subtitle: '관측성, 알림, 장애 드릴, 오탐률 검증을 함께 다룬 프로젝트',
    stage: '완료',
    highlight: 'Prometheus, Grafana, trace 로그, 드릴 자동화를 하나의 운영 흐름으로 연결했습니다.',
    stacks: ['Kotlin', 'Spring Boot', 'Micrometer', 'Prometheus', 'Grafana', 'Docker'],
    stats: [
      { label: 'Latency Median TTD', value: '10.00s' },
      { label: 'Error Median TTD', value: '15.02s' },
      { label: 'False Positive', value: '0건' },
    ],
    bullets: [
      'Trace ID/MDC 구조화 로그와 표준 에러 응답 구축',
      'Alert rule, baseline reset, 반복 드릴 자동화까지 운영 루프 구현',
      'drill/normal 트래픽 분리와 정상 구간 오탐률 0건 검증 완료',
    ],
    evidence: [
      'Week2~4 운영 리포트와 drill 결과 JSON을 충분히 확보했습니다.',
      'README에는 주요 장애 사례별 트러블슈팅 기록을 구조화해 남겼습니다.',
    ],
    colorClass: 'tone-emerald',
  },
]

const showcaseSummary = {
  title: '3-in-1 Project',
  body: '결제, 주문·정산, 운영 관측까지 3개의 서로 다른 프로젝트를 하나의 큰 프로젝트로 정리했습니다.\n성능 측정 결과, 운영 리포트, 트러블슈팅 기록도 함께 확인하실 수 있으며, Vue 대시보드에서 Kafka UI, Grafana, Prometheus까지 자연스럽게 이어지도록 빌드했습니다.',
}

const demoChecklist = [
  {
    name: 'Payment Core API 대시보드',
    url: 'http://localhost:5173',
    description: '프로젝트 전체 소개와 결제 생성·승인·취소 흐름을 함께 확인할 수 있습니다.',
  },
  {
    name: 'Kafka UI / Adminer / Redis Commander',
    url: 'http://localhost:18082 / 18080 / 18081',
    description: 'Kafka UI에서는 이벤트 흐름을, Adminer에서는 저장된 데이터를, Redis Commander에서는 캐시 상태를 확인할 수 있습니다.',
  },
  {
    name: 'Grafana / Prometheus',
    url: 'http://localhost:13000 / 19090',
    description: 'Grafana에서는 메트릭과 알림 흐름을, Prometheus에서는 수집 상태와 쿼리 결과를 확인할 수 있습니다.',
  },
]

const payments = ref([])
const nextCursorId = ref(null)
const hasNext = ref(false)
const loadingList = ref(false)
const creatingDemo = ref(false)
const listError = ref('')

const paymentId = ref('')
const approvalKey = ref('dashboard-approve-key')
const cancellationKey = ref('dashboard-cancel-key')
const actionMessage = ref('')
const actionLoading = ref(false)

const recentLatencyMs = ref(null)
const totalRequests = ref(0)
const successRequests = ref(0)
const failedRequests = ref(0)
const lastTraceId = ref('-')

const counts = computed(() => {
  const result = { PENDING: 0, APPROVED: 0, FAILED: 0, CANCELED: 0 }
  for (const payment of payments.value) {
    if (result[payment.status] !== undefined) {
      result[payment.status] += 1
    }
  }
  return result
})

const selectedPayment = computed(() => payments.value.find((payment) => String(payment.id) === String(paymentId.value)) || null)

const walkthroughScenarios = [
  {
    id: 'lifecycle',
    title: '결제 생명주기',
    summary: '생성 → 승인 → 취소 상태 전이를 한 번에 확인합니다.',
    tags: ['PaymentService', '상태 전이', 'Outbox'],
  },
  {
    id: 'idempotency',
    title: '승인 멱등성',
    summary: '같은 Idempotency-Key로 승인 요청을 반복해도 한 번만 처리되는 흐름입니다.',
    tags: ['Idempotency-Key', 'Pessimistic Lock', '409/200'],
  },
  {
    id: 'webhook',
    title: '웹훅 보정',
    summary: 'PG 상태 웹훅으로 결제 상태를 보정하고 동일 transmission ID 중복을 확인합니다.',
    tags: ['Webhook', 'Reconciliation', 'Duplicate'],
  },
  {
    id: 'outbox',
    title: '트랜잭션 아웃박스',
    summary: '결제 변경 이벤트를 DB에 저장한 뒤 로컬 발행 시뮬레이션으로 상태를 전환합니다.',
    tags: ['Atomicity', 'PENDING', 'PUBLISHED'],
  },
]

const selectedWalkthroughId = ref('lifecycle')
const walkthroughRunning = ref(false)
const walkthroughMessage = ref('시나리오를 선택하고 실행하면 실제 localhost API 처리 결과가 아래에 기록됩니다.')
const walkthroughSteps = ref([])
const selectedWalkthrough = computed(() => walkthroughScenarios.find((scenario) => scenario.id === selectedWalkthroughId.value))

function appendWalkthroughStep(title, detail, status = '완료') {
  walkthroughSteps.value.push({ title, detail, status })
}

async function runWalkthrough() {
  if (walkthroughRunning.value) {
    return
  }

  walkthroughRunning.value = true
  walkthroughSteps.value = []
  walkthroughMessage.value = `${selectedWalkthrough.value.title} 시나리오를 실행하고 있습니다.`

  try {
    if (selectedWalkthroughId.value === 'lifecycle') {
      const created = await createPayment(makeDemoPayload())
      appendWalkthroughStep('1. 결제 생성', `POST /api/v1/payments → paymentId=${created.body.id}, status=${created.body.status}`)
      const approved = await approvePayment(created.body.id, `walkthrough-approve-${created.body.id}`)
      appendWalkthroughStep('2. 결제 승인', `PaymentGateway(local) → status=${approved.body.status}, traceId=${approved.traceId ?? '-'}`)
      const canceled = await transitionPayment(created.body.id, 'cancel')
      appendWalkthroughStep('3. 결제 취소', `상태 전이 완료 → status=${canceled.body.status}, traceId=${canceled.traceId ?? '-'}`)
      appendWalkthroughStep('4. 이벤트 적재', '생성·승인·취소 이벤트가 트랜잭션 아웃박스에 저장됩니다.')
      walkthroughMessage.value = '결제 생명주기 시나리오가 완료되었습니다.'
    }

    if (selectedWalkthroughId.value === 'idempotency') {
      const created = await createPayment(makeDemoPayload())
      const key = `walkthrough-idempotency-${created.body.id}`
      appendWalkthroughStep('1. 결제 생성', `paymentId=${created.body.id}, status=${created.body.status}`)
      const first = await approvePayment(created.body.id, key)
      appendWalkthroughStep('2. 첫 승인 요청', `Idempotency-Key=${key} → ${first.body.status}`)
      const retry = await approvePayment(created.body.id, key)
      appendWalkthroughStep('3. 같은 키 재요청', `기존 승인 결과 재사용 → ${retry.body.status}`)
      appendWalkthroughStep('4. 보호 지점', '같은 키는 200으로 재응답하고, 다른 키는 상태 전이 충돌로 409를 반환합니다.')
      walkthroughMessage.value = '승인 멱등성 시나리오가 완료되었습니다.'
    }

    if (selectedWalkthroughId.value === 'webhook') {
      const created = await createPayment(makeDemoPayload())
      const transmissionId = `walkthrough-webhook-${created.body.id}`
      const payload = {
        eventType: 'PAYMENT_STATUS_CHANGED',
        createdAt: new Date().toISOString(),
        data: {
          paymentKey: `local-payment-key-${created.body.id}`,
          orderId: String(created.body.orderId),
          status: 'DONE',
        },
      }
      appendWalkthroughStep('1. 결제 생성', `paymentId=${created.body.id}, status=${created.body.status}`)
      const processed = await sendPaymentStatusWebhook(payload, transmissionId)
      appendWalkthroughStep('2. 웹훅 수신', `transmissionId=${transmissionId} → ${processed.body.result}`)
      const duplicate = await sendPaymentStatusWebhook(payload, transmissionId)
      appendWalkthroughStep('3. 중복 웹훅', `같은 transmission ID → ${duplicate.body.result}`)
      appendWalkthroughStep('4. 상태 보정', `PENDING → APPROVED, 원문 payload는 재처리용으로 저장됩니다.`)
      walkthroughMessage.value = '웹훅 보정과 중복 방지 시나리오가 완료되었습니다.'
    }

    if (selectedWalkthroughId.value === 'outbox') {
      const created = await createPayment(makeDemoPayload())
      const before = await getOutboxMetrics()
      appendWalkthroughStep('1. 결제 생성', `paymentId=${created.body.id} → 아웃박스 pending=${before.body.pendingEvents}`)
      const published = await publishOutbox()
      appendWalkthroughStep('2. 로컬 발행 시뮬레이션', `PENDING → PUBLISHED, published=${published.body.publishedEvents}`)
      const after = await getOutboxMetrics()
      appendWalkthroughStep('3. 운영 지표 확인', `pending=${after.body.pendingEvents}, retrying=${after.body.retryingEvents}, failed=${after.body.failedEvents}`)
      appendWalkthroughStep('4. 설계 범위', '현재는 외부 Kafka 없이 DB 상태 전환으로 검증하며, 실제 브로커는 다음 확장 지점입니다.')
      walkthroughMessage.value = '트랜잭션 아웃박스 시나리오가 완료되었습니다.'
    }
  } catch (error) {
    appendWalkthroughStep('시나리오 오류', `${error.message} / traceId=${error.traceId ?? '-'}`, '확인 필요')
    walkthroughMessage.value = '시나리오 실행 중 오류가 발생했습니다. 아래 기록과 traceId를 확인하세요.'
  } finally {
    walkthroughRunning.value = false
  }
}

function makeDemoPayload(index = 0) {
  const timestamp = Date.now()
  return {
    orderId: 700000 + timestamp + index,
    idempotencyKey: `dashboard-demo-${timestamp}-${index}`,
    amount: 10000 + index * 1000,
    method: 'CARD',
  }
}

function selectPayment(payment) {
  paymentId.value = String(payment.id)
  actionMessage.value = `paymentId=${payment.id} 선택됨. 현재 상태=${payment.status}`
}

async function loadFirstPage() {
  loadingList.value = true
  listError.value = ''
  try {
    const startedAt = performance.now()
    const data = await getPaymentsCursor(null, 20)
    totalRequests.value += 1
    successRequests.value += 1
    lastTraceId.value = data.traceId || '-'
    recentLatencyMs.value = Math.round((performance.now() - startedAt) * 100) / 100

    payments.value = data.body.content
    hasNext.value = data.body.hasNext
    nextCursorId.value = data.body.nextCursorId

    if (payments.value.length === 0) {
      actionMessage.value = '목록이 비어 있습니다. 아래의 샘플 결제 생성 버튼으로 데모 데이터를 먼저 만드세요.'
    } else if (!selectedPayment.value) {
      paymentId.value = String(payments.value[0].id)
      actionMessage.value = `paymentId=${payments.value[0].id} 자동 선택됨. 상태=${payments.value[0].status}`
    }
  } catch (error) {
    totalRequests.value += 1
    failedRequests.value += 1
    if (error.traceId) {
      lastTraceId.value = error.traceId
    }
    listError.value = error.message
  } finally {
    loadingList.value = false
  }
}

async function loadMore() {
  if (!hasNext.value || !nextCursorId.value) {
    return
  }

  loadingList.value = true
  listError.value = ''
  try {
    const data = await getPaymentsCursor(nextCursorId.value, 20)
    totalRequests.value += 1
    successRequests.value += 1
    lastTraceId.value = data.traceId || '-'
    payments.value = [...payments.value, ...data.body.content]
    hasNext.value = data.body.hasNext
    nextCursorId.value = data.body.nextCursorId
  } catch (error) {
    totalRequests.value += 1
    failedRequests.value += 1
    if (error.traceId) {
      lastTraceId.value = error.traceId
    }
    listError.value = error.message
  } finally {
    loadingList.value = false
  }
}

async function runAction(action) {
  const id = Number(paymentId.value)
  if (!id) {
    actionMessage.value = 'paymentId를 입력하세요.'
    return
  }

  actionLoading.value = true
  actionMessage.value = ''

  try {
    const updated = action === 'approve'
      ? await approvePayment(id, approvalKey.value.trim() || `dashboard-approve-${Date.now()}`)
      : await cancelPayment(id, cancellationKey.value.trim() || `dashboard-cancel-${Date.now()}`)
    totalRequests.value += 1
    successRequests.value += 1
    lastTraceId.value = updated.traceId || '-'
    actionMessage.value = `${action.toUpperCase()} 성공: status=${updated.body.status} / traceId=${updated.traceId ?? '-'}`
    await loadFirstPage()
  } catch (error) {
    totalRequests.value += 1
    failedRequests.value += 1
    if (error.traceId) {
      lastTraceId.value = error.traceId
    }
    actionMessage.value = `실패: ${error.message} / traceId=${error.traceId ?? '-'}`
  } finally {
    actionLoading.value = false
  }
}

async function createSingleDemoPayment() {
  creatingDemo.value = true
  listError.value = ''

  try {
    const created = await createPayment(makeDemoPayload())
    totalRequests.value += 1
    successRequests.value += 1
    lastTraceId.value = created.traceId || '-'
    paymentId.value = String(created.body.id)
    actionMessage.value = `샘플 결제 생성 성공: paymentId=${created.body.id} / status=${created.body.status} / traceId=${created.traceId ?? '-'}`
    await loadFirstPage()
  } catch (error) {
    totalRequests.value += 1
    failedRequests.value += 1
    if (error.traceId) {
      lastTraceId.value = error.traceId
    }
    actionMessage.value = `샘플 결제 생성 실패: ${error.message} / traceId=${error.traceId ?? '-'}`
  } finally {
    creatingDemo.value = false
  }
}

async function seedDemoPayments(count = 5) {
  creatingDemo.value = true
  listError.value = ''

  try {
    const createdIds = []
    let latestTraceId = '-'

    for (let index = 0; index < count; index += 1) {
      const created = await createPayment(makeDemoPayload(index))
      createdIds.push(created.body.id)
      latestTraceId = created.traceId || latestTraceId
      totalRequests.value += 1
      successRequests.value += 1
    }

    lastTraceId.value = latestTraceId
    paymentId.value = String(createdIds[0])
    actionMessage.value = `샘플 결제 ${count}건 생성 완료: 첫 paymentId=${createdIds[0]} / 마지막 traceId=${latestTraceId}`
    await loadFirstPage()
  } catch (error) {
    totalRequests.value += 1
    failedRequests.value += 1
    if (error.traceId) {
      lastTraceId.value = error.traceId
    }
    actionMessage.value = `샘플 일괄 생성 실패: ${error.message} / traceId=${error.traceId ?? '-'}`
  } finally {
    creatingDemo.value = false
  }
}

async function loadOperations() {
  operationsLoading.value = true
  operationsError.value = ''
  try {
    const [webhook, outbox, audit] = await Promise.all([
      getWebhookMetrics(),
      getOutboxMetrics(),
      getAuditEvents(),
    ])
    operations.value = { webhook: webhook.body, outbox: outbox.body, audit: audit.body }
  } catch (error) {
    operationsError.value = `${error.message} / traceId=${error.traceId ?? '-'}`
  } finally {
    operationsLoading.value = false
  }
}

async function publishOutboxFromDashboard() {
  operationsLoading.value = true
  operationsError.value = ''
  operationsMessage.value = ''
  try {
    const result = await publishOutbox()
    operationsMessage.value = `로컬 처리 완료: pending=${result.body.pendingEvents}, published=${result.body.publishedEvents}`
    await loadOperations()
  } catch (error) {
    operationsError.value = `${error.message} / traceId=${error.traceId ?? '-'}`
  } finally {
    operationsLoading.value = false
  }
}

async function retryOutboxEventFromDashboard() {
  const eventId = failedOutboxEventId.value.trim()
  if (!eventId) {
    operationsError.value = '재처리할 FAILED 아웃박스 eventId를 입력해주세요.'
    return
  }

  operationsLoading.value = true
  operationsError.value = ''
  operationsMessage.value = ''
  try {
    const result = await retryOutboxEvent(eventId)
    operationsMessage.value = `수동 재처리 대기열 등록: eventId=${result.body.eventId}, status=${result.body.status}`
    await loadOperations()
  } catch (error) {
    operationsError.value = `${error.message} / traceId=${error.traceId ?? '-'}`
  } finally {
    operationsLoading.value = false
  }
}

watch(activeView, async (view) => {
  if (view === 'payment-demo' && !loadingList.value && payments.value.length === 0 && !listError.value) {
    await loadFirstPage()
  }
  if (view === 'operations' && !operationsLoading.value) {
    await loadOperations()
  }
})

onMounted(async () => {
  if (activeView.value === 'payment-demo') {
    await loadFirstPage()
  }
})

</script>

<template>
  <div class="hero">
    <div class="hero-copy">
      <span class="eyebrow">결제 API 운영 개요</span>
      <h1>시스템 개요</h1>
      <p class="hero-description">
        결제 도메인, 주문·정산과 운영 흐름까지 한눈에
      </p>
    </div>
    <div class="hero-panel card gradient-card">
      <div class="hero-summary">
        <strong class="hero-summary-title">{{ showcaseSummary.title }}</strong>
        <p class="hero-summary-body">{{ showcaseSummary.body }}</p>
      </div>
    </div>
  </div>

  <div class="tabs">
    <button :class="['tab-button', { active: activeView === 'walkthrough' }]" @click="activeView = 'walkthrough'">
      프로젝트 가이드
    </button>
    <button :class="['tab-button', { active: activeView === 'showcase' }]" @click="activeView = 'showcase'">
      개요
    </button>
    <button :class="['tab-button', { active: activeView === 'payment-demo' }]" @click="activeView = 'payment-demo'">
      Payment Demo
    </button>
    <button :class="['tab-button', { active: activeView === 'operations' }]" @click="activeView = 'operations'">
      운영 대시보드
    </button>
  </div>

  <template v-if="activeView === 'walkthrough'">
    <div class="section-header">
      <h2>기능별 처리 과정</h2>
      <p class="meta">백엔드 코드를 읽지 않아도 결제 프로젝트의 핵심 설계와 실제 API 흐름을 단계별로 확인할 수 있습니다.</p>
    </div>

    <section class="walkthrough-layout section">
      <div class="card scenario-list">
        <div class="section-header compact">
          <h3>시연 시나리오</h3>
          <p class="meta">각 버튼은 localhost 백엔드 API를 실제로 호출합니다.</p>
        </div>
        <button
          v-for="scenario in walkthroughScenarios"
          :key="scenario.id"
          :class="['scenario-button', { active: selectedWalkthroughId === scenario.id }]"
          @click="selectedWalkthroughId = scenario.id"
        >
          <strong>{{ scenario.title }}</strong>
          <span>{{ scenario.summary }}</span>
          <small>{{ scenario.tags.join(' · ') }}</small>
        </button>
        <button class="scenario-run-button" @click="runWalkthrough" :disabled="walkthroughRunning">
          {{ walkthroughRunning ? '시나리오 실행 중...' : '선택한 시나리오 실행' }}
        </button>
      </div>

      <div class="card">
        <div class="section-header compact">
          <span class="project-badge">{{ selectedWalkthrough?.title }}</span>
          <h3>{{ selectedWalkthrough?.summary }}</h3>
          <p class="meta">{{ walkthroughMessage }}</p>
        </div>

        <div class="flow-rail">
          <div class="flow-node">Client</div>
          <span>→</span>
          <div class="flow-node">Controller</div>
          <span>→</span>
          <div class="flow-node">Service</div>
          <span>→</span>
          <div class="flow-node">DB / PG</div>
        </div>

        <div v-if="walkthroughSteps.length === 0" class="empty-state walkthrough-empty">
          실행 결과가 여기에 시간순으로 표시됩니다.
        </div>
        <div v-else class="scenario-log">
          <article v-for="(step, index) in walkthroughSteps" :key="step.title + '-' + index" class="scenario-log-item">
            <div class="timeline-dot"></div>
            <div>
              <div class="row space-between">
                <strong>{{ step.title }}</strong>
                <span :class="['status-pill', { warning: step.status !== '완료' }]">{{ step.status }}</span>
              </div>
              <p>{{ step.detail }}</p>
            </div>
          </article>
        </div>
      </div>
    </section>

    <section class="project-grid section">
      <article class="card project-card tone-blue">
        <div class="project-head"><span class="project-badge">API 품질</span><span class="project-stage">핵심</span></div>
        <h3>멱등성과 동시성</h3>
        <p class="project-highlight">중복 승인 요청을 안전하게 처리하고, 서로 다른 승인 키가 동시에 들어오면 한 요청만 상태를 변경합니다.</p>
        <div class="chip-list"><span class="chip">Idempotency-Key</span><span class="chip">Pessimistic Lock</span><span class="chip">409 Conflict</span></div>
      </article>
      <article class="card project-card tone-violet">
        <div class="project-head"><span class="project-badge">외부 연동</span><span class="project-stage">경계</span></div>
        <h3>PG·웹훅 보정</h3>
        <p class="project-highlight">PG 승인 결과와 웹훅 상태를 분리하고, 실패·일시 장애·중복 이벤트를 서로 다른 운영 흐름으로 기록합니다.</p>
        <div class="chip-list"><span class="chip">Gateway Boundary</span><span class="chip">Retry</span><span class="chip">Reconciliation</span></div>
      </article>
      <article class="card project-card tone-emerald">
        <div class="project-head"><span class="project-badge">운영 근거</span><span class="project-stage">문서화</span></div>
        <h3>검증과 의사결정</h3>
        <p class="project-highlight">H2/MySQL 테스트, ADR, 트러블슈팅 기록을 연결해 구현뿐 아니라 선택 이유와 장애 대응 과정까지 설명합니다.</p>
        <div class="chip-list"><span class="chip">75 Tests</span><span class="chip">ADR</span><span class="chip">Troubleshooting</span></div>
      </article>
    </section>
  </template>

  <template v-else-if="activeView === 'showcase'">
    <section class="section">
      <div class="section-header">
        <h2>주요 구현</h2>
        <p class="meta">각 프로젝트의 역할과 구현 내용</p>
      </div>

      <div class="project-grid">
        <article v-for="project in projects" :key="project.id" :class="['project-card', 'card', project.colorClass]">
          <div class="project-head">
            <span class="project-badge">{{ project.badge }}</span>
            <span class="project-stage">{{ project.stage }}</span>
          </div>
          <h3>{{ project.title }}</h3>
          <p class="project-subtitle">{{ project.subtitle }}</p>
          <p class="project-highlight">{{ project.highlight }}</p>

          <div class="chip-list">
            <span v-for="stack in project.stacks" :key="stack" class="chip">{{ stack }}</span>
          </div>

          <div class="stats-grid">
            <div v-for="stat in project.stats" :key="stat.label" class="stat-box">
              <strong>{{ stat.value }}</strong>
              <span>{{ stat.label }}</span>
            </div>
          </div>

          <div class="detail-group">
            <h4>주요 구현</h4>
            <ul>
              <li v-for="item in project.bullets" :key="item">{{ item }}</li>
            </ul>
          </div>

          <div class="detail-group">
            <h4>운영 포인트</h4>
            <ul>
              <li v-for="item in project.evidence" :key="item">{{ item }}</li>
            </ul>
          </div>
        </article>
      </div>
    </section>

    <section class="section two-column-grid">
      <article class="card">
        <div class="section-header compact">
          <h2>이렇게 이어집니다</h2>
        </div>

        <div class="timeline">
          <div class="timeline-item">
            <div class="timeline-dot"></div>
            <div>
              <strong>1. Payment Core API</strong>
              <p>Payment Demo에서 결제를 생성하고 승인·취소까지 진행하면서, 사용자의 요청이 어떤 식으로 처리되는지 확인할 수 있습니다.</p>
            </div>
          </div>
          <div class="timeline-item">
            <div class="timeline-dot"></div>
            <div>
              <strong>2. Order Settlement Async</strong>
              <p>Kafka UI, Adminer, Redis Commander에서 주문·정산 이벤트가 어떻게 발행되고 저장되는지 확인할 수 있습니다.</p>
            </div>
          </div>
          <div class="timeline-item">
            <div class="timeline-dot"></div>
            <div>
              <strong>3. Backend Observability Lab</strong>
              <p>Grafana와 Prometheus에서 사용자의 요청과 이벤트 처리 과정을 확인할 수 있습니다.</p>
            </div>
          </div>
        </div>
      </article>

      <article class="card">
        <div class="section-header compact">
          <h2>시스템 개요</h2>
          <p class="meta">각 프로젝트별 호스트</p>
        </div>

        <div class="url-list">
          <div v-for="item in demoChecklist" :key="item.name" class="url-card">
            <strong>{{ item.name }}</strong>
            <code>{{ item.url }}</code>
            <p>{{ item.description }}</p>
          </div>
        </div>
      </article>
    </section>
  </template>

  <template v-else-if="activeView === 'operations'">
    <div class="section-header">
      <h2>운영 대시보드</h2>
      <p class="meta">웹훅 재처리 상태와 트랜잭션 아웃박스 적재 상태를 로컬에서 확인합니다.</p>
    </div>

    <section class="card section demo-helper-card">
      <div class="demo-helper-top">
        <div>
          <h3>운영 확인 포인트</h3>
          <p class="meta">외부 Kafka, 클라우드 모니터링, 실결제 API 없이 로컬 DB에 저장된 운영 지표만 조회합니다.</p>
        </div>
        <div class="row wrap-row">
          <button @click="loadOperations" :disabled="operationsLoading">지표 새로고침</button>
          <button @click="publishOutboxFromDashboard" :disabled="operationsLoading">대기 아웃박스 로컬 처리</button>
          <input v-model="failedOutboxEventId" placeholder="FAILED eventId" aria-label="FAILED outbox eventId" />
          <button @click="retryOutboxEventFromDashboard" :disabled="operationsLoading">실패 이벤트 재처리</button>
        </div>
      </div>
      <p v-if="operationsError" class="meta error-text">{{ operationsError }}</p>
      <p v-else class="meta">{{ operationsMessage || '운영 지표를 확인할 준비가 되었습니다.' }}</p>
    </section>

    <div class="project-grid">
      <section class="card project-card tone-blue">
        <div class="project-head">
          <span class="project-badge">Webhook</span>
          <span class="project-stage">로컬 지표</span>
        </div>
        <h3>웹훅 상태</h3>
        <div class="metrics">
          <div class="metric">전체: {{ operations.webhook?.totalEvents ?? '-' }}</div>
          <div class="metric">처리 완료: {{ operations.webhook?.processedEvents ?? '-' }}</div>
          <div class="metric">재처리: {{ operations.webhook?.reprocessedEvents ?? '-' }}</div>
          <div class="metric">무시: {{ operations.webhook?.ignoredEvents ?? '-' }}</div>
        </div>
        <p class="meta">transmission ID 중복 수신을 기록하고, 저장된 원문 기준 재처리 흐름을 확인할 수 있습니다.</p>
      </section>

      <section class="card project-card tone-violet">
        <div class="project-head">
          <span class="project-badge">Outbox</span>
          <span class="project-stage">로컬 시뮬레이션</span>
        </div>
        <h3>트랜잭션 아웃박스</h3>
        <div class="metrics">
          <div class="metric">대기: {{ operations.outbox?.pendingEvents ?? '-' }}</div>
          <div class="metric">재시도 대기: {{ operations.outbox?.retryingEvents ?? '-' }}</div>
          <div class="metric">처리 완료: {{ operations.outbox?.publishedEvents ?? '-' }}</div>
          <div class="metric">실패: {{ operations.outbox?.failedEvents ?? '-' }}</div>
        </div>
        <p class="meta">결제 변경 이벤트를 결제 트랜잭션과 함께 저장하고, 실패 이벤트는 eventId를 지정해 수동 재처리 대기열로 되돌릴 수 있습니다.</p>
      </section>
    </div>

    <section class="card section">
      <div class="section-header compact">
        <div>
          <h2>최근 운영 감사 이력</h2>
          <p class="meta">수동 운영 작업의 결과·대상·traceId를 최근 50건까지 확인합니다.</p>
        </div>
      </div>
      <div v-if="operations.audit.length === 0" class="empty-state meta">
        아직 기록된 운영 감사 이력이 없습니다.
      </div>
      <div v-else class="table-wrapper">
        <table class="audit-table">
          <thead>
            <tr>
              <th>시각</th>
              <th>작업</th>
              <th>대상</th>
              <th>결과</th>
              <th>traceId</th>
              <th>상세</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="event in operations.audit" :key="event.id">
              <td>{{ event.createdAt }}</td>
              <td>{{ event.operation }}</td>
              <td>{{ event.targetId || '-' }}</td>
              <td>
                <span :class="['status-pill', { warning: event.outcome !== 'SUCCESS' }]">
                  {{ event.outcome }}
                </span>
              </td>
              <td>{{ event.traceId || '-' }}</td>
              <td>{{ event.detail || '-' }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </template>

  <template v-else>
    <div class="section-header">
      <h2>Payment Demo Test</h2>
      <p class="meta">결제 생성부터 승인, 취소까지 실제 API 흐름을 바로 확인할 수 있습니다.</p>
    </div>

    <section class="card section demo-helper-card">
      <div class="demo-helper-top">
        <div>
          <h3>바로 시연해보기</h3>
          <p class="meta">
            백엔드가 실행 중이면 샘플 결제를 만든 뒤, 목록에서 선택해서 승인/취소 흐름을 바로 보여줄 수 있습니다.
          </p>
        </div>
        <div class="row wrap-row">
          <button @click="createSingleDemoPayment" :disabled="creatingDemo">샘플 1건 생성</button>
          <button @click="seedDemoPayments(5)" :disabled="creatingDemo">샘플 5건 생성</button>
          <button @click="loadFirstPage" :disabled="loadingList">목록 다시 조회</button>
        </div>
      </div>
      <ul class="helper-list">
        <li>1) 샘플 생성 → 2) 목록에서 행 선택 → 3) `APPROVE` → 4) 다시 선택 후 `CANCEL` 순서로 보여주면 됩니다.</li>
        <li>연결이 되지 않으면 project1 백엔드가 `http://localhost:8080`에서 실행 중인지 먼저 확인해주세요.</li>
      </ul>
    </section>

    <div class="grid">
      <section class="card">
        <div class="row space-between">
          <h2>결제 목록 (Cursor)</h2>
          <button @click="loadFirstPage" :disabled="loadingList">새로고침</button>
        </div>

        <p v-if="listError" class="meta error-text">{{ listError }}</p>
        <p v-else class="meta">최근 조회 응답시간: {{ recentLatencyMs ?? '-' }}ms</p>

        <table>
          <thead>
            <tr>
              <th>ID</th>
              <th>Order</th>
              <th>Amount</th>
              <th>Method</th>
              <th>Status</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="payment in payments"
              :key="payment.id"
              :class="{ selected: String(payment.id) === String(paymentId) }"
              @click="selectPayment(payment)"
            >
              <td>{{ payment.id }}</td>
              <td>{{ payment.orderId }}</td>
              <td>{{ payment.amount }}</td>
              <td>{{ payment.method }}</td>
              <td>{{ payment.status }}</td>
            </tr>
          </tbody>
        </table>

        <p v-if="payments.length === 0 && !loadingList && !listError" class="meta empty-state">
          아직 조회된 결제가 없습니다. 위의 샘플 생성 버튼으로 바로 데모 데이터를 준비할 수 있습니다.
        </p>

        <div class="row" style="margin-top: 12px">
          <button @click="loadMore" :disabled="loadingList || !hasNext">더 보기</button>
          <span class="meta">hasNext={{ hasNext }}</span>
        </div>
      </section>

      <section class="card">
        <h2>상태 전이</h2>
        <div class="row">
          <input v-model="paymentId" placeholder="paymentId" />
        </div>
        <div class="row" style="margin-top: 10px">
          <input v-model="approvalKey" placeholder="Idempotency-Key (승인용)" />
        </div>
        <div class="row" style="margin-top: 10px">
          <input v-model="cancellationKey" placeholder="Idempotency-Key (취소용)" />
        </div>
        <p class="meta" style="margin-top: 10px">
          현재 선택: {{ selectedPayment ? `paymentId=${selectedPayment.id} / status=${selectedPayment.status}` : '아직 선택된 결제가 없습니다.' }}
        </p>
        <div class="row" style="margin-top: 10px">
          <button @click="runAction('approve')" :disabled="actionLoading">APPROVE</button>
          <button @click="runAction('cancel')" :disabled="actionLoading">CANCEL</button>
        </div>
        <p class="meta" style="margin-top: 10px">{{ actionMessage || '준비되었습니다. 샘플 결제를 만들거나 목록에서 하나를 선택해보세요.' }}</p>

        <h2 style="margin-top: 20px">지표 요약</h2>
        <div class="metrics">
          <div class="metric">TOTAL_REQUESTS: {{ totalRequests }}</div>
          <div class="metric">SUCCESS_REQUESTS: {{ successRequests }}</div>
          <div class="metric">FAILED_REQUESTS: {{ failedRequests }}</div>
          <div class="metric">LAST_TRACE_ID: {{ lastTraceId }}</div>
          <div class="metric">PENDING: {{ counts.PENDING }}</div>
          <div class="metric">APPROVED: {{ counts.APPROVED }}</div>
          <div class="metric">FAILED: {{ counts.FAILED }}</div>
          <div class="metric">CANCELED: {{ counts.CANCELED }}</div>
        </div>
      </section>
    </div>
  </template>
</template>
