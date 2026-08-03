# ADR: 승인 멱등성·운영 관측성·로컬 실행 검증 정리

- 상태: Accepted
- 날짜: 2026-07-29
- 범위: NEXT-38 ~ NEXT-44

## Context

승인 API의 Redis 보조 멱등성 계층, MySQL cursor 조회, outbox worker, health endpoint, 내부 운영 token 검증, Docker 실행 경계를 각각 구현한 뒤 같은 로컬 환경에서 재현 가능한 검증 근거가 필요했습니다. 실제 Toss API, Kafka, 클라우드 DB, 실결제는 이번 범위에 포함하지 않았습니다.

## Decisions

### 1. Redis guard는 DB 행 잠금보다 먼저 시도합니다

동일 결제에 대한 여러 애플리케이션 인스턴스의 요청이 DB `FOR UPDATE` 대기열에 먼저 들어가지 않도록 Redis `SET NX EX` 보조 게이트를 선행합니다. Redis가 진행 중인 lease를 반환하면 `409 PAYMENT_IDEMPOTENCY_IN_PROGRESS`로 빠르게 응답합니다. 승인 상태와 최종 멱등성 판단은 기존 DB 행 잠금과 `approval_idempotency_key`가 담당합니다.

Redis 장애 시에는 DB 경로로 fallback할 수 있어 Redis를 최종 저장소로 사용하지 않습니다.

### 2. Cursor 성능은 동일 조건의 offset 비교로만 기록합니다

MySQL 8.0 Compose 환경에서 800건을 적재하고 page size 20, 각 API 60회 호출 조건으로 측정했습니다. 단일 로컬 실행값은 재현 조건과 함께 기록하며 운영 latency로 일반화하지 않습니다.

### 3. Outbox worker는 DB 상태 전환 경계를 재사용합니다

worker는 `PaymentOutboxService.publishPending()`을 호출하고, 기존 publisher의 재시도·최종 실패·감사 흐름을 재사용합니다. 로컬 시연과 수동 검증의 예측 가능성을 위해 기본값은 비활성화하고 `PAYMENT_OUTBOX_WORKER_ENABLED=true`로 명시적으로 켭니다.

### 4. Health endpoint는 애플리케이션 의존성 상태를 분리해 반환합니다

`GET /api/v1/health`는 DB 연결 검증과 Redis ping을 각각 `up`, `down`, `disabled`로 표시합니다. DB가 내려가거나 활성화된 Redis가 내려가면 `503`과 `degraded`를 반환하도록 구성했습니다. 컨테이너 smoke 검증에서는 DB와 Redis가 모두 `up`인 `200` 응답을 확인했습니다.

### 5. 내부 운영 token은 길이 제한과 constant-time 비교를 함께 적용합니다

운영 token은 로그에 남기지 않고, 빈 값·설정 누락·최대 길이 초과를 거절합니다. 실제 인증·권한 시스템을 새로 도입하지 않고 현재 로컬 운영 API의 최소 보안 경계를 보강하는 범위로 제한했습니다.

### 6. Dockerfile은 multi-stage build를 사용합니다

JDK 단계에서 wrapper로 `bootJar`를 생성하고 JRE 단계에는 실행 jar만 복사합니다. Windows 작업 트리의 CRLF wrapper가 Linux 컨테이너에서 실행되지 않는 문제가 확인되어 Docker build 단계에서 wrapper 줄바꿈을 정규화합니다. Compose API는 MySQL·Redis healthcheck 이후 시작하도록 구성했습니다.

## Validation

- H2 전체 테스트: 74개, 실패 0개, 오류 0개, skipped 0개
- Compose MySQL·Redis `prod` 전체 테스트: 74개, 실패 0개, 오류 0개, skipped 0개
- Docker image build 및 `docker compose up -d --build` 성공
- 컨테이너 health endpoint: `status=ok`, `database=up`, `redis=up`
- local gateway 기반 생성·승인·동일 승인 멱등키 재요청 smoke 성공
- Dashboard `npm run build` 성공

## Constraints

- 실제 Toss secret key와 Toss API, 실결제, Kafka, 클라우드·외부 유료 서비스는 사용하지 않았습니다.
- 다중 애플리케이션 인스턴스의 실제 네트워크 부하 측정과 Redis 장애 주입은 후속 운영 환경 검증 범위입니다.
- GitHub Actions 실행과 GitHub push는 수행하지 않았습니다.
