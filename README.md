# kotlin-payment-core-api

결제 상태 전이가 엉켜서 approve/cancel이 자꾸 깨지는 문제가 있었어요. 그래서 상태 규칙을 명확히 제한하는 API를 직접 만들어봤습니다.

## 무엇을 해결했나

- 상태 머신: PENDING → APPROVED → CANCELED. 다른 이동은 409로 막음
- 조회 성능: 기존 offset 페이징을 유지하면서 cursor(keyset) 페이징을 추가해 비교 가능하게 구성
- 추적: 에러 응답에 traceId를 넣어서 로그에서 요청 경로를 따라갈 수 있게 함
- 테스트: Vue로 간단한 대시보드를 붙여서 API 동작을 직접 확인할 수 있게 함
- 정산 계산: basis point 수수료율과 명시적인 반올림 규칙으로 수수료·정산 금액을 재현 가능하게 계산
- 승인 멱등성: DB 행 잠금을 기준으로 두고 Redis를 분산 중복 진입 차단용 보조 계층으로 사용
- 정산 원장: 승인 성공 시 원금·수수료율·수수료·정산 금액 스냅샷과 `SETTLEMENT_REQUESTED` 이벤트를 함께 기록

## 상태 전이 규칙

- PENDING → approve → APPROVED
- APPROVED → cancel → CANCELED
- 나머지 전이 → 409 INVALID_PAYMENT_STATUS_TRANSITION
- APPROVED 상태에서 같은 `Idempotency-Key`로 approve 재요청 → 기존 승인 응답 반환
- APPROVED 상태에서 다른 `Idempotency-Key`로 approve 재요청 → 409 반환
- 승인 처리 시 결제 행에 비관적 쓰기 잠금을 적용해 동시 승인 경합을 직렬화
- 외부 PG 승인 경계를 `PaymentGateway` 인터페이스로 분리하고 성공·거절·일시 장애를 구분
- Toss 결제 상태 웹훅의 중복 방지와 내부 결제 상태 보정 처리

실제로는 이 규칙이 애매하면 장애가 자주 나서, 아예 코드로 고정해 두는 편이 낫다고 생각했어요.

## 기술 스택

- Kotlin 1.9, Spring Boot 3.3
- JPA, H2 / MySQL
- springdoc OpenAPI
- Vue 3 + Vite

## 실행 방법

### 백엔드

```bash
./gradlew bootRun
```

- API: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/api-docs`

OpenAPI 문서는 다음 책임별 tag로 나뉩니다.

- `Payments`: 결제 생성·조회·승인·취소와 offset/cursor 조회
- `Toss Webhooks`: Toss 결제 상태 웹훅 수신과 transmission ID 중복 보정
- `Internal Webhook Operations`: 보호된 웹훅 replay·지표 조회
- `Internal Outbox Operations`: 보호된 outbox 발행·재시도·지표 조회
- `Internal Audit`: 내부 운영 작업 감사 이력

문서 제목·버전과 핵심 endpoint 경로는 `PaymentControllerIntegrationTest`에서 회귀 검증합니다. 설계 근거는 [OpenAPI 계약 ADR](docs/adr/2026-08-02-openapi-contract.md)에 정리했습니다.

### 대시보드

```bash
cd dashboard
npm install
npm run dev
```

- Dashboard: `http://localhost:5173`
- `/api` 요청은 Vite proxy로 `http://localhost:8080`에 전달됩니다.

### 로컬 의존성 컨테이너

MySQL 8.0과 Redis는 `compose.yaml`로 실행할 수 있습니다. 기존 `payment-core-mysql` 컨테이너가 `3307`을 사용하고 있어 Compose MySQL은 호스트 `3308`로 노출합니다.

```bash
docker compose up -d --build
docker compose ps
```

Compose는 API·MySQL·Redis를 함께 기동합니다. API 컨테이너는 MySQL과 Redis healthcheck가 통과한 뒤 시작하며, 상태는 `GET http://localhost:8080/api/v1/health`에서 확인할 수 있습니다.

```json
{"status":"ok","components":{"database":"up","redis":"up"}}
```

로컬 Spring Boot 프로세스에서 연결할 때는 다음 환경변수를 사용합니다.

```powershell
$env:SPRING_PROFILES_ACTIVE = "prod"
$env:DB_URL = "jdbc:mysql://localhost:3308/payment_core?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
$env:DB_USERNAME = "payment"
$env:DB_PASSWORD = "payment-password"
$env:DB_DRIVER_CLASS = "com.mysql.cj.jdbc.Driver"
$env:JPA_DIALECT = "org.hibernate.dialect.MySQLDialect"
$env:SPRING_FLYWAY_LOCATIONS = "classpath:db/migration/mysql"
$env:PAYMENT_GATEWAY_PROVIDER = "local"
$env:PAYMENT_WEBHOOK_REPLAY_TOKEN = "local-compose-test-token"
$env:PAYMENT_OUTBOX_WORKER_ENABLED = "false"
$env:REDIS_URL = "redis://localhost:6379"
$env:PAYMENT_IDEMPOTENCY_REDIS_ENABLED = "true"
```

컨테이너 종료는 `docker compose down`을 사용합니다. 이 compose 파일은 로컬 검증용이며 운영 배포 설정이나 외부 유료 서비스를 포함하지 않습니다.

Dockerfile은 JDK build stage에서 `bootJar`를 만들고 JRE runtime stage에는 실행 jar만 복사합니다. Windows 작업 트리의 Gradle wrapper 줄바꿈 차이는 build stage에서 정규화합니다.

### 로컬 부하 테스트

Node 내장 `fetch`를 사용하는 측정 스크립트로 조회 API의 동시 요청을 확인할 수 있습니다. 서버가 먼저 실행되어 있어야 하며, 실행 결과의 평균·p50·p95·실패율을 그대로 기록합니다.

```powershell
$env:REQUESTS = "500"
$env:CONCURRENCY = "20"
npm run load-test
```

기본 대상은 `GET /api/v1/payments/cursor?size=20`이며 `BASE_URL`, `LOAD_TEST_PATH`, `EXPECTED_STATUS`로 조정할 수 있습니다. 실제 측정값은 실행 환경·데이터 건수·DB·동시성 조건과 함께 기록해야 하며, 측정하지 않은 수치는 성능 근거로 사용하지 않습니다.

2026-07-26 기본 H2 환경에서 별도 워밍업 없이 서버 readiness 확인 후 100건·동시성 10으로 측정했습니다. Cursor 조회는 100건 모두 HTTP 200이었고, 평균 71.04ms, p50 14.26ms, p95 562.78ms, 최솟값 7.17ms, 최댓값 658.41ms, 실패율 0%였습니다. 이 결과는 단일 로컬 실행 조건의 기록이며 MySQL 운영 성능이나 처리량의 근거로 확대하지 않습니다.

### CI 검증 범위

GitHub Actions는 backend build, H2 전체 테스트, MySQL 8.0 service 기반 전체 테스트, Vue/Vite production build를 각각 검증합니다. MySQL job은 `db/migration/mysql` Flyway 위치와 local provider를 사용하며 실제 Toss API나 유료 외부 서비스는 호출하지 않습니다.

운영 준비 검증에서는 Redis guard를 DB 행 잠금보다 먼저 실행해 동일 승인 요청이 이미 진행 중이면 빠르게 `409 PAYMENT_IDEMPOTENCY_IN_PROGRESS`를 반환하도록 했습니다. DB의 `approval_idempotency_key`와 행 잠금은 최종 일관성 기준으로 유지합니다. Outbox worker는 `PAYMENT_OUTBOX_WORKER_ENABLED=true`로 켜며, 기본값은 수동 대시보드 확인을 위해 `false`입니다. 관련 선택 근거와 검증 결과는 [운영 준비 ADR](docs/adr/2026-07-29-operations-readiness-next38-44.md)에 정리했습니다.

정산 계산은 `SettlementAmountCalculator`가 담당합니다. 수수료율은 basis point로 받고(`100 bps = 1%`), 수수료는 소수 둘째 자리에서 `HALF_UP`으로 반올림한 뒤 정산 금액을 원금에서 차감합니다. 정책과 제한은 [정산 금액 계산 ADR](docs/adr/2026-07-26-settlement-amount-calculation.md)에 정리했습니다.

승인 멱등성의 최종 기준은 `payments.approval_idempotency_key`와 승인 행 잠금입니다. `PAYMENT_IDEMPOTENCY_REDIS_ENABLED=true`인 환경에서는 Redis `SET NX EX`를 보조 게이트로 사용해 같은 결제·승인 키의 동시 진입을 빠르게 거절합니다. Redis 연결이 일시적으로 unavailable이면 TTL과 DB 멱등성으로 처리할 수 있도록 DB 경로로 fallback합니다. 관련 설계는 [Redis 승인 멱등성 ADR](docs/adr/2026-07-26-redis-approval-idempotency.md)에 정리했습니다.

승인 성공 트랜잭션에서는 `payment_settlements`에 계산 결과를 보존하고, 기존 승인 이벤트와 함께 `SETTLEMENT_REQUESTED` 아웃박스 이벤트를 기록합니다. 현재 publisher는 로컬 구현이며 실제 Redis·Kafka·외부 Toss API 연결은 검증 범위에 포함하지 않았습니다. [정산 원장·승인 이벤트 ADR](docs/adr/2026-07-26-settlement-ledger-approval-event.md)에서 경계를 설명합니다.

## clone만 해도 바로 보기

루트에 `viewer.html`을 넣어뒀어요. clone만 해도 브라우저로 열면 아키텍처 다이어그램과 성능 비교 차트를 바로 확인할 수 있습니다.

```bash
git clone git@github.com:sugowslt/kotlin-payment-core-api.git
open kotlin-payment-core-api/viewer.html
```

별도 빌드나 배포는 필요 없습니다.

## 트러블슈팅

- Cursor vs Offset: 처음엔 offset 페이징을 썼다가 데이터가 늘어나면서 cursor로 바꿨어요.
- traceId: 필터에서 생성해서 응답 헤더로 반환하고, 에러 핸들러에서도 로그에 포함하도록 했습니다.
- Swagger: springdoc 2.6에서 `/api-docs` 경로가 달라져서 확인이 필요했어요.

### 실제 작업 중 발생한 테스트 컴파일 오류

승인 멱등키 구현을 검증하는 과정에서 `PaymentServiceTest`가 변경된 repository 메서드명을 따라가지 못해 컴파일 오류가 발생했습니다. 테스트 mock을 현재 soft-delete 조건을 포함한 repository 메서드와 일치시키고 전체 테스트를 다시 실행해 해결했습니다.

상세한 원인 분석과 대안 비교는 [트러블슈팅 기록](docs/troubleshooting/2026-07-24-approval-idempotency-test-compile.md)에 남겼습니다.

승인 동시성 제어 방식과 선택 근거는 [ADR 기록](docs/adr/2026-07-24-approval-concurrency-lock.md)에 정리했습니다.

외부 PG 연동 경계와 오류 분류 기준은 [ADR 기록](docs/adr/2026-07-24-payment-gateway-boundary.md)에 정리했습니다.

Toss adapter 테스트 중 발생한 컴파일·mock 요청·Basic 인증 오류와 해결 과정은 [트러블슈팅 기록](docs/troubleshooting/2026-07-24-toss-gateway-adapter-test.md)에 정리했습니다.

Toss gateway의 4xx 거절·5xx 및 timeout 재시도 분류와 회귀 테스트 기준은 [장애 분류 ADR](docs/adr/2026-07-29-toss-gateway-failure-contract.md)에 정리했습니다. 실제 Toss API와 secret key는 검증에 사용하지 않았습니다.

웹훅 transmission ID 중복 방지와 상태 보정 기준은 [ADR 기록](docs/adr/2026-07-24-toss-webhook-reconciliation.md)에 정리했습니다.

웹훅 원문 재처리와 로컬 지표 endpoint의 운영 기준은 [ADR 기록](docs/adr/2026-07-24-webhook-replay-operations.md)에 정리했습니다.

결제 변경 이벤트를 DB에 먼저 기록하는 트랜잭션 아웃박스와 `운영 대시보드` 탭은 [ADR 기록](docs/adr/2026-07-24-transactional-outbox-local-operations.md)에 정리했습니다. 현재 아웃박스 처리는 외부 브로커가 아닌 로컬 상태 전환 시뮬레이션입니다.

아웃박스 publisher가 일시 실패하면 기본 3회까지 재시도하고, 초과 시 `FAILED`와 마지막 오류를 저장합니다. `PAYMENT_OUTBOX_MAX_RETRIES`와 `PAYMENT_OUTBOX_RETRY_DELAY_SECONDS`로 로컬 검증 정책을 조정할 수 있으며, 선택 근거는 [재시도 정책 ADR](docs/adr/2026-07-24-outbox-retry-policy.md)에 정리했습니다.

`FAILED` 이벤트는 `POST /api/v1/internal/outbox/{eventId}/retry`와 `X-Webhook-Replay-Token`으로 수동 복구할 수 있습니다. 대상 상태와 token을 검증한 뒤 `PENDING`으로 되돌리며, 운영 기준은 [수동 복구 ADR](docs/adr/2026-07-24-outbox-manual-recovery.md)에 정리했습니다.

결제 취소도 `PaymentGateway` 경계를 통과한 뒤 내부 상태를 변경합니다. `local` provider는 외부 호출 없이 동작하고, Toss adapter는 취소 endpoint·취소 사유·idempotency key·오류 재시도를 mock으로 검증했습니다. 자세한 선택 근거는 [Toss 취소 ADR](docs/adr/2026-07-24-toss-cancellation-boundary.md)에 정리했습니다.

취소 endpoint도 `Idempotency-Key`를 필수로 받아 같은 key 재요청에는 기존 `CANCELED` 응답을 재사용하고, 다른 key에는 `409`를 반환합니다. 취소 멱등성 기준은 [취소 멱등성 ADR](docs/adr/2026-07-24-cancellation-idempotency.md)에 정리했습니다.

취소 요청 본문은 `cancelReason`과 선택적인 `cancelAmount`를 받습니다. 부분 취소 중에는 `APPROVED` 상태를 유지하고, 누적 취소 금액이 결제 금액에 도달하면 `CANCELED`로 전환합니다. 상세 모델은 [부분 취소·이력 ADR](docs/adr/2026-07-24-partial-cancellation-history.md)에 정리했습니다.

아웃박스는 `PAYMENT_OUTBOX_WORKER_ENABLED=true`로 scheduled worker를 활성화할 수 있습니다. 기본값은 `false`이며, worker 주기는 `PAYMENT_OUTBOX_WORKER_FIXED_DELAY_MS`로 설정합니다. 여러 worker의 동일 이벤트 경합은 DB 행 잠금으로 제어하고, 근거는 [아웃박스 worker ADR](docs/adr/2026-07-24-outbox-worker-concurrency.md)에 정리했습니다.

Docker Desktop의 `dockerInference` 소켓 초기화 오류와 복구 과정은 [트러블슈팅 기록](docs/troubleshooting/2026-07-24-docker-inference-socket.md)에 정리했습니다.

2026-07-29 Docker image build에서 Windows CRLF Gradle wrapper가 Linux 컨테이너에서 `./gradlew: not found`로 인식된 오류가 있었습니다. Dockerfile build 단계에서 wrapper 줄바꿈을 정규화해 해결했습니다.

2026-07-29 Compose MySQL 측정에서는 800건·page size 20·각 60회 조건에서 Offset 평균 67.07ms/p95 83.10ms, Cursor 평균 63.46ms/p95 75.68ms가 관찰되었습니다. 단일 로컬 측정값이며 운영 성능의 보증값이 아닙니다.

로컬 대시보드 기동 확인 명령의 시간 초과와 분리 검증 과정은 [트러블슈팅 기록](docs/troubleshooting/2026-07-24-local-dashboard-startup-check.md)에 정리했습니다.

부분 취소 이력 테스트에서 발생한 relaxed mock 기본 반환값 오류와 해결 과정은 [트러블슈팅 기록](docs/troubleshooting/2026-07-24-partial-cancellation-test-mock.md)에 정리했습니다.

내부 운영 API는 `X-Webhook-Replay-Token`을 공통 authorizer로 검증합니다. 아웃박스 발행·재등록과 웹훅 replay의 성공·실패는 traceId와 함께 감사 테이블에 기록되며, 최근 이력은 token이 필요한 `/api/v1/internal/audit-events`에서 확인할 수 있습니다. 설계 근거는 [내부 운영 보안·감사 ADR](docs/adr/2026-07-25-internal-operations-security-audit.md)에 정리했습니다.

운영 대시보드의 `운영 대시보드` 탭에서도 웹훅·아웃박스 지표와 최근 감사 이력을 함께 확인할 수 있습니다. 감사 이력에는 작업명, 대상, 결과, traceId, 실패 상세가 표시됩니다.

운영 프로파일은 `SPRING_PROFILES_ACTIVE=prod`로 활성화합니다. 운영에서는 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `PAYMENT_WEBHOOK_REPLAY_TOKEN`, `TOSS_PAYMENT_SECRET_KEY`를 환경변수로 주입하고, `PAYMENT_OUTBOX_WORKER_ENABLED` 기본값은 `true`입니다. 실제 Toss API와 유료 외부 서비스는 로컬 검증에 사용하지 않았습니다.

`prod` 프로파일은 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`가 없으면 시작하지 않으며, `DB_DRIVER_CLASS`와 `JPA_DIALECT`는 MySQL 기본값을 사용합니다. 다른 JDBC 드라이버나 방언을 사용할 때만 해당 환경변수를 별도로 덮어씁니다.

schema는 Flyway V1 migration으로 관리하며 기본 H2 환경은 `db/migration/h2`, `prod` 프로파일은 `db/migration/mysql` 위치를 사용합니다. JPA는 `validate`만 수행하고, H2의 `CLOB`과 MySQL의 `TEXT` 차이는 database별 migration으로 분리했습니다. 설계 근거는 [Flyway schema migration ADR](docs/adr/2026-07-26-flyway-schema-migration.md)에 정리했습니다.

### 페이지네이션 비교 기준

- Offset: `GET /api/v1/payments?page=0&size=20`
- Cursor(keyset): `GET /api/v1/payments/cursor?size=20`
- Cursor 요청의 다음 페이지는 응답의 `nextCursorId`를 `cursorId`로 전달합니다.
- 성능 수치는 H2/MySQL, 데이터 건수, 반복 횟수, 애플리케이션 상태에 따라 달라지므로 실행 환경과 측정 조건을 함께 기록합니다.

2026-07-24 현재 코드의 H2 로컬 측정에서는 800건 적재, 10회 워밍업, 각 API 60회 호출 조건에서 다음 결과가 나왔습니다.

- Offset: 평균 3.66ms / p95 5.35ms
- Cursor: 평균 2.85ms / p95 4.21ms

이번 조건에서는 cursor가 평균 0.81ms, p95 1.14ms 낮았습니다. H2 인메모리·단일 로컬 클라이언트 측정이므로 MySQL 운영 성능이나 동시 부하 상황의 결론으로 확대하지 않습니다.

처음 프로젝트를 확인할 때는 Vue 대시보드의 프로젝트 가이드 탭을 먼저 열어주세요. 결제 생명주기, 승인 멱등성, 웹훅 보정, 아웃박스 시나리오를 실제 localhost API로 실행하고 요청·응답·traceId를 단계별로 확인할 수 있습니다. 화면 구성 기준은 [Walkthrough ADR](docs/adr/2026-07-24-project-walkthrough-dashboard.md)에 정리했습니다.
