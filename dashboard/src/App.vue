<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { createPayment, getPaymentsCursor, transitionPayment } from './api'

const activeView = ref('showcase')

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
    const updated = await transitionPayment(id, action)
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

watch(activeView, async (view) => {
  if (view === 'payment-demo' && !loadingList.value && payments.value.length === 0 && !listError.value) {
    await loadFirstPage()
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
      <span class="eyebrow">Kotlin Backend Portfolio Showcase</span>
      <h1>포트폴리오 대시보드</h1>
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
    <button :class="['tab-button', { active: activeView === 'showcase' }]" @click="activeView = 'showcase'">
      Portfolio Showcase
    </button>
    <button :class="['tab-button', { active: activeView === 'payment-demo' }]" @click="activeView = 'payment-demo'">
      Payment Live Demo
    </button>
  </div>

  <template v-if="activeView === 'showcase'">
    <section class="section">
      <div class="section-header">
        <h2>포트폴리오 구성</h2>
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
            <h4>함께 보면 좋은 포인트</h4>
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
              <p>Payment Live Demo에서 결제를 생성하고 승인·취소까지 진행하면서, 사용자의 요청이 어떤 식으로 처리되는지 확인할 수 있습니다.</p>
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
          <h2>포트폴리오 대시보드</h2>
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

  <template v-else>
    <div class="section-header">
      <h2>Payment Live Demo Test</h2>
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
