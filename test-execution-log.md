# Test Execution Log

## 2026-02-13

### Command
- `./gradlew test` (`.\gradlew.bat test` on Windows)

### Result
- BUILD SUCCESSFUL
- Unit tests: `PaymentServiceTest` 3개 통과
- Integration tests: `PaymentControllerIntegrationTest` 통과
- Context load test: `PaymentCoreApiApplicationTests` 통과

### Notes
- 기본 테스트 환경은 H2 in-memory DB
- Gradle deprecated warning은 존재하지만 테스트 성공/기능 검증에는 영향 없음

## 2026-02-20

### Command
- 성능 측정 스크립트 실행 (PowerShell)
	- 데이터 적재: 결제 800건
	- 반복 호출: 목록 API 60회

### Result
- Offset 목록 API: 평균 11.93ms / p95 15.20ms
- Cursor 목록 API: 평균 10.61ms / p95 12.26ms

### Notes
- keyset(cursor) 방식이 H2 기준 p95 약 2.94ms 개선
- MySQL 재측정은 Docker daemon 비가동으로 미수행

## 2026-02-25

### Command
- Vue 대시보드 시나리오 실행
	- 백엔드: `./gradlew.bat bootRun`
	- 대시보드: `cd dashboard && npm run dev`
	- 샘플 데이터: 12건 적재 (`POST /api/v1/payments`)

### Result
- 시나리오 A(목록 조회/더보기): 정상
- 시나리오 B(승인/취소 상태 전이): 정상
- 시나리오 C(지표 요약 카드): 정상

### Notes
- NEXT-10 완료 기준에서 스크린샷은 선택사항으로 운영
- 실행 로그 + 시나리오 체크 결과로 완료 증빙

## 2026-02-25 (MySQL)

### Command
- MySQL 연결로 백엔드 실행
	- `DB_URL=jdbc:mysql://localhost:3307/payment_core ... ./gradlew.bat bootRun`
- 벤치마크 스크립트 실행
	- `./scripts/next9-mysql-measure.ps1 -BaseUrl http://localhost:8080 -SeedCount 800 -LoopCount 60 -PageSize 20`
- 추가 검증(조회 전용)
	- 목록 API 80회 x 3라운드

### Result
- 1차(60회)
	- offset 평균 19.47ms / p95 21.08ms
	- cursor 평균 17.89ms / p95 22.04ms
- 추가 3라운드(80회)
	- round1: offset p95 18.34ms / cursor p95 19.54ms
	- round2: offset p95 19.99ms / cursor p95 18.25ms
	- round3: offset p95 18.51ms / cursor p95 17.82ms

### Notes
- 단회 측정 대비 반복 측정에서 cursor p95가 소폭 우세한 라운드가 더 많음
- MySQL에서도 keyset(cursor) 전략을 유지하고 후속 미세조정 대상으로 관리

## 2026-02-25 (NEXT-12)

### Command
- 백엔드 테스트: `./gradlew.bat test`
- 대시보드 빌드: `cd dashboard && npm run build`

### Result
- traceId 성공 응답 통합 테스트 포함 전체 테스트 통과
- 대시보드 빌드 성공

### Notes
- 대시보드 지표에 `TOTAL/SUCCESS/FAILED/LAST_TRACE_ID` 반영
- 운영 점검 시나리오에서 traceId 기반 로그 추적 가능

## 2026-02-25 (NEXT-13)

### Command
- 운영 체크리스트/템플릿 문서화
	- `traceid-incident-playbook.md`

### Result
- traceId 기반 장애 재현 절차, 로그 조회 템플릿, MySQL 점검 쿼리 템플릿 정리 완료

### Notes
- 추후 학습과 운영 점검 시 동일 템플릿으로 재현 가능

## 2026-07-24 (승인 멱등키 검증)

### Command

- `cmd /d /c gradlew.bat test --no-daemon`

### Result

- 최초 실행에서 `PaymentServiceTest` 컴파일 오류 발생
- 원인: repository 메서드명 변경 후 테스트 mock이 이전 이름을 사용
- 수정: `existsByIdempotencyKeyAndDeletedFalse`, `findByIdAndDeletedFalse` 기준으로 mock 동기화
- 수정 후 결과: `BUILD SUCCESSFUL`
- 전체 테스트: 25개
- 실패: 0개
- 오류: 0개
- 건너뜀: 0개

### Notes

- 승인 API의 동일 `Idempotency-Key` 재요청 통합 테스트를 포함합니다.
- 본 기록에는 실제 실행 중 발생한 컴파일 오류와 해결 과정만 남겼습니다.

## 2026-07-24 (페이지네이션 성능 재측정)

### Environment and command

- 환경: Spring Boot 로컬 실행 + H2 in-memory
- 데이터: 결제 800건
- 워밍업: offset/cursor 각 5회
- 측정: 각 endpoint 60회, page size 20, 단일 로컬 클라이언트
- 비교 endpoint:
  - `GET /api/v1/payments?page=0&size=20`
  - `GET /api/v1/payments/cursor?size=20`

### Result

| 방식 | 평균 | p95 | 최소 | 최대 |
| --- | ---: | ---: | ---: | ---: |
| Offset | 3.66ms | 5.35ms | 2.70ms | 7.01ms |
| Cursor | 2.85ms | 4.21ms | 2.20ms | 6.58ms |

### Interpretation

- 이번 H2 로컬 조건에서 cursor가 offset보다 평균 0.81ms, p95 1.14ms 낮았습니다.
- 이 결과는 인메모리 DB·단일 클라이언트·첫 페이지 조회 기준입니다.
- MySQL, 대량 offset, 동시 부하, 네트워크 지연이 포함된 운영 성능으로 일반화하지 않습니다.
- 후속 검증에서는 동일 조건의 MySQL 측정과 큰 `page`/`cursorId` 구간 비교가 필요합니다.

## 2026-07-24 (MySQL 승인 동시성 검증)

### Environment and command

- 환경: Docker Desktop 4.52.0, Docker Engine 29.0.1, MySQL 8.4
- 컨테이너: `payment-core-mysql`, `localhost:3307`
- 검증 대상: 서로 다른 `Idempotency-Key`를 사용한 승인 요청 2건 동시 실행
- 실행 방식: MySQL datasource 환경변수를 주입한 Gradle 통합 테스트

### Result

- 동시 승인 테스트: 성공
- 결과 상태: 한 요청 `200 OK`, 다른 요청 `409 Conflict`
- MySQL 기준 전체 테스트: 28개
- 실패: 0개
- 오류: 0개
- 건너뜀: 0개

### Notes

- 승인 경로의 `PESSIMISTIC_WRITE` 잠금이 실제 MySQL 트랜잭션에서 동작하는지 확인했습니다.
- 테스트는 로컬 2개 요청 기준이며, 운영 규모의 부하·잠금 대기시간·타임아웃 성능을 의미하지 않습니다.

## 2026-07-24 (PG gateway 경계 및 오류 분류)

### Change

- `PaymentGateway` 인터페이스와 `LocalPaymentGateway` 구현 추가
- 승인 성공 시 `providerTransactionId` 저장
- PG 명확한 거절은 `FAILED`로 기록
- PG 일시 장애는 `PENDING`을 유지해 재시도 가능하도록 분류

### Validation

- gateway 성공 결과를 승인 응답에 반영하는 서비스 테스트 통과
- gateway 거절 시 `FAILED` 상태 기록 테스트 통과
- gateway 일시 장애 시 `PENDING` 상태 유지 테스트 통과
- H2 기준 전체 테스트 30개 성공
- MySQL 기준 전체 테스트 30개 성공

### Notes

- 실제 Toss API와 인증정보는 사용하지 않았습니다.
- HTTP adapter, 타임아웃·재시도 정책, `paymentKey` 매핑은 아래 Toss adapter 검증에서 반영했습니다.

## 2026-07-24 (Toss HTTP adapter mock 검증)

### Validation

- Toss 승인 요청 endpoint: `/v1/payments/confirm`
- Basic 인증 헤더와 `Idempotency-Key` 검증
- `paymentKey/orderId/amount` 요청 본문 검증
- 5xx 응답 1회 재시도 검증
- `Payment-Key` 누락 시 외부 요청 전송 전 400 분기 검증
- adapter mock 테스트 3개 성공
- H2 기준 전체 테스트 33개 성공
- MySQL 기준 전체 테스트 33개 성공

### Notes

- `PAYMENT_GATEWAY_PROVIDER=toss`일 때만 Toss adapter가 활성화됩니다.
- 기본값은 외부 네트워크를 사용하지 않는 `local` provider입니다.
- 실제 Toss API 호출은 수행하지 않았습니다.

## 2026-07-24 (Toss 결제 상태 웹훅 보정 검증)

### Validation

- `PAYMENT_STATUS_CHANGED` 웹훅 수신 endpoint 추가
- `DONE` 웹훅으로 `PENDING → APPROVED` 보정 확인
- `EXPIRED` 웹훅으로 `PENDING → FAILED` 보정 확인
- 동일 transmission ID 재전송 시 `DUPLICATE` 응답 확인
- transmission ID 누락 시 `400 INVALID_WEBHOOK` 확인
- H2 기준 전체 테스트 36개 성공
- MySQL 기준 전체 테스트 36개 성공

### Notes

- 실제 Toss 서버와 통신하지 않고 로컬 통합 테스트로 검증했습니다.
- 결제 상태 웹훅에는 공식 서명 헤더가 제공되지 않는 현재 문서 기준으로 transmission ID 중복 방지와 상태 보정에 집중했습니다.

## 2026-07-24 (웹훅 재처리·운영 지표 검증)

### Validation

- 웹훅 원문 payload 저장 확인
- 결제 미존재로 무시된 이벤트를 replay token으로 재처리하는 흐름 확인
- 이미 처리된 이벤트의 재처리 차단 확인
- replay token 없는 지표 요청 `403` 확인
- 전체·처리·재처리·무시 이벤트 수 지표 반환 확인
- H2 기준 전체 테스트 38개 성공
- MySQL 기준 전체 테스트 38개 성공

### Notes

- replay token 기본값은 빈 값이며, 이 경우 내부 endpoint는 비활성화됩니다.
- 실제 외부 API, 유료 모니터링 서비스, 클라우드 리소스는 사용하지 않았습니다.

## 2026-07-24 (트랜잭션 아웃박스·운영 대시보드 검증)

### Validation

- 결제 생성·승인·취소 및 웹훅 상태 보정 시 아웃박스 이벤트 저장 확인
- 아웃박스 `PENDING` 지표와 로컬 `PUBLISHED` 전환 endpoint 확인
- replay token 없는 내부 운영 endpoint 보호 유지 확인
- H2 기준 전체 테스트 39개 성공
- MySQL 기준 전체 테스트 39개 성공

### Notes

- `publish`는 외부 Kafka 발행이 아닌 로컬 DB 상태 전환 시뮬레이션입니다.
- 대시보드는 `운영 대시보드` 탭에서 웹훅·아웃박스 지표를 조회합니다.
- 실제 Toss API, 외부 브로커, 유료 서비스는 사용하지 않았습니다.

## 2026-07-24 (프로젝트 Walkthrough 대시보드 검증)

### Validation

- 프로젝트 가이드 탭과 네 가지 시연 시나리오 렌더링 확인
- 결제 생명주기 시나리오에서 생성·승인·취소 결과와 traceId 표시 확인
- 승인 멱등성 시나리오에서 같은 키 재요청 결과 재사용 확인
- Vue npm run build 성공

### Notes

- 시연 버튼은 실제 localhost API를 호출하지만 외부 PG·Kafka·유료 서비스에는 접근하지 않습니다.
- 시나리오 실행으로 생성된 샘플 결제는 로컬 DB에 남을 수 있습니다.

## 2026-07-24 (트랜잭션 아웃박스 재시도 정책 검증)

### Validation

- publisher 성공 시 `PUBLISHED` 전환 확인
- 일시 실패 시 `retryCount` 증가와 `PENDING` 유지 확인
- 최대 재시도 도달 시 `FAILED`, `lastError`, `nextAttemptAt` 정리 확인
- H2 기준 전체 테스트 42개 성공
- MySQL 기준 전체 테스트 42개 성공

### Notes

- 로컬 publisher는 외부 Kafka 발행이 아닌 경계 검증용 컴포넌트입니다.
- 실제 외부 브로커, 유료 서비스, 클라우드 리소스는 사용하지 않았습니다.

## 2026-07-24 (FAILED 아웃박스 수동 복구 검증)

### Validation

- `FAILED` 이벤트를 token 보호 endpoint로 `PENDING`에 재등록 확인
- 재등록 시 retry budget과 다음 시도 시각 초기화 확인
- `PENDING` 이벤트 수동 재처리 요청 시 `409 INVALID_OUTBOX_STATUS` 확인
- token 없는 재처리 요청 시 `403 WEBHOOK_REPLAY_FORBIDDEN` 확인
- H2 기준 전체 테스트 45개 성공
- MySQL 기준 전체 테스트 45개 성공
- Vue npm run build 성공

### Notes

- 수동 재처리는 실제 broker를 호출하지 않고 DB 상태와 로컬 publisher 경계를 검증합니다.
- 외부 비용이 발생하는 서비스와 실결제 API는 사용하지 않았습니다.

## 2026-07-24 (Toss 결제 취소 gateway 경계 검증)

### Validation

- 승인된 결제 취소 시 gateway 성공 후 `CANCELED` 전환 확인
- gateway 취소 거절 시 내부 결제가 `APPROVED`로 유지되는 단위 테스트 추가
- Toss cancel endpoint, Basic 인증, `cancelReason`, 취소 idempotency key 요청 검증
- Toss 취소 provider transaction id 누락 시 사전 요청 오류 확인
- H2 기준 전체 테스트 47개 성공
- MySQL 기준 전체 테스트 47개 성공

### Notes

- Toss HTTP 요청은 `MockRestServiceServer`로만 검증했습니다.
- 기본 provider는 `local`이며 실제 Toss 인증정보·실결제·유료 서비스는 사용하지 않았습니다.

## 2026-07-24 (결제 취소 멱등성 검증)

### Validation

- 취소 endpoint의 `Idempotency-Key` 필수 header 확인
- 같은 취소 key 재요청 시 기존 `CANCELED` 응답과 gateway 재호출 없음 확인
- 다른 취소 key 재요청 시 `409 INVALID_PAYMENT_STATUS_TRANSITION` 확인
- PG 취소 거절 시 `APPROVED` 상태 유지 확인
- H2 기준 전체 테스트 50개 성공
- MySQL 기준 전체 테스트 50개 성공

### Notes

- 웹훅 취소 보정은 transmission ID를 별도 취소 식별자로 사용합니다.
- 실제 Toss API와 유료 서비스는 사용하지 않았습니다.

## 2026-07-24 (부분 취소·취소 이력 검증)

### Validation

- 부분 취소 금액 3,000원 후 잔액 취소 시 `APPROVED → CANCELED` 전환 확인
- `canceledAmount` 누적 응답과 `payment_cancellations` 저장 확인
- 남은 금액 초과 취소 요청 `400 INVALID_PAYMENT_CANCELLATION` 확인
- Toss mock 요청에 `cancelAmount` 전달 확인
- H2 기준 전체 테스트 52개 성공
- MySQL 기준 전체 테스트 52개 성공

### Notes

- 현재는 전액·부분 취소만 지원하며 가상계좌 환불 계좌는 별도 범위입니다.
- 실제 Toss API, 외부 비용 서비스는 사용하지 않았습니다.
- 부분 취소 이력 mock 오류와 해결 과정은 [트러블슈팅 기록](docs/troubleshooting/2026-07-24-partial-cancellation-test-mock.md)에 남겼습니다.

## 2026-07-24 (아웃박스 자동 worker·동시 실행 방지 검증)

### Validation

- scheduled worker가 `PaymentOutboxService.publishPending()`을 위임하는 단위 테스트 확인
- worker 기본 비활성 설정과 fixed delay 환경변수 확인
- 두 publish 요청이 같은 PENDING 이벤트를 한 번만 PUBLISHED로 전환하는 통합 테스트 확인
- H2 기준 전체 테스트 54개 성공
- MySQL 기준 전체 테스트 54개 성공

### Notes

- 실제 broker는 연결하지 않고 local publisher로 검증했습니다.
- worker는 대시보드 수동 시연과 충돌하지 않도록 기본 비활성입니다.

## 2026-07-25 (내부 운영 보안·감사 로그 검증)

### Validation

- 아웃박스·웹훅 운영 endpoint가 공통 constant-time token authorizer를 사용하는지 확인
- token 없는 감사 이력 조회가 `403 WEBHOOK_REPLAY_FORBIDDEN`을 반환하는지 확인
- 아웃박스 publish 성공 이력에 `OUTBOX_PUBLISH`, `SUCCESS`가 저장되는지 확인
- 웹훅 replay 이력에 대상 transmission ID와 요청 traceId가 저장되는지 확인
- `prod` 프로파일의 `ddl-auto=validate`, Toss provider, worker 활성화 기본값 확인
- H2 기준 전체 테스트 56개 성공, 실패 0개, 오류 0개, skipped 0개
- MySQL 기준 전체 테스트 56개 성공, 실패 0개, 오류 0개, skipped 0개

### Notes

- replay token과 Toss secret은 저장하지 않고 환경변수로만 주입합니다.
- 외부 Toss API, 외부 감사 서비스, 유료 서비스는 사용하지 않았습니다.

## 2026-07-26 (운영 감사 이력 대시보드 연동)

### Validation

- 운영 대시보드가 웹훅 지표·아웃박스 지표·최근 감사 이력을 함께 조회하도록 연결
- 성공·실패 결과, 대상 ID, traceId, 상세 메시지 표시 구조 확인
- `npm run build` 성공
- 사용자 소유 `dashboard/package.json`, `dashboard/package-lock.json`은 변경하지 않음

### Notes

- 백엔드의 token 보호 감사 endpoint를 기존 대시보드의 운영 token 헤더로 호출합니다.
- 외부 감사 서비스와 유료 서비스는 사용하지 않았습니다.

## 2026-07-26 (Flyway schema migration 검증)

### Validation

- Hibernate 기본 schema 전략을 `create-drop`에서 `validate`로 변경
- Flyway V1 초기 schema와 MySQL 전용 dependency 추가
- H2 migration에서 `CLOB`을 사용하고 MySQL migration에서 `TEXT`를 사용하도록 위치 분리
- H2 기준 전체 테스트 56개 성공, 실패 0개, 오류 0개, skipped 0개
- MySQL 기준 전체 테스트 56개 성공, 실패 0개, 오류 0개, skipped 0개

### Troubleshooting

- 첫 Flyway 실행에서 H2 schema validation이 `payment_outbox_events.payload`의 `VARCHAR`와 JPA `CLOB` 불일치로 실패했습니다. migration의 payload 타입을 H2에서 `CLOB`으로 맞춘 뒤 테스트가 통과했습니다.
- MySQL 검증 시작 시 Docker Desktop이 `dockerInference`와 `docker-secrets-engine\engine.sock` stale AF_UNIX socket 오류로 시작되지 않았습니다. 사용자가 Docker Desktop을 정상 실행한 뒤 컨테이너 검증을 재개했습니다.
- 첫 MySQL 테스트 명령은 PowerShell 변수 보간 실수로 database명이 `=false&allowPublicKeyRetrieval...`로 조합되어 56개 중 33개가 context 초기화에서 실패했습니다. URL 조합을 `String.Format`으로 수정한 뒤 재실행했습니다.
- 올바른 URL로 실행한 첫 MySQL 테스트에서는 `TIMESTAMP` 초 단위 정밀도 때문에 정렬 테스트 1개가 실패했습니다. MySQL V2 migration에서 `TIMESTAMP(6)`으로 보정한 뒤 전체 테스트가 통과했습니다.
- 공장 초기화, WSL 전체 종료, Docker 데이터 삭제, 유료 서비스 호출은 수행하지 않았습니다.
- 상세 원인·대안·선택 근거는 [Flyway/Docker troubleshooting 기록](docs/troubleshooting/2026-07-26-flyway-docker-validation.md)에 남겼습니다.

## 2026-07-26 (금액·수수료·정산 계산)

### Validation

- 수수료율을 basis point로 계산하는 순수 `SettlementAmountCalculator` 추가
- 소수 둘째 자리 `HALF_UP` 반올림 확인
- 0%·100% 수수료, 0 이하 금액, 수수료율 범위, 소수 셋째 자리 금액 검증
- 동일 입력 재실행 시 동일 결과가 나오는지 확인
- 정산 계산 단위 테스트 8개 성공
- H2 전체 테스트 64개 성공, 실패 0개, 오류 0개, skipped 0개

### Notes

- 기존 Payment API, entity, migration은 변경하지 않았습니다.
- 실제 정산 원장·지급 상태·가맹점별 수수료율은 후속 범위로 남겼습니다.
- 외부 Toss API, 실결제, 유료 서비스는 사용하지 않았습니다.

## 2026-07-26 (Redis 승인 멱등성·정산 원장·승인 이벤트)

### Validation

- Redis key가 결제 ID와 SHA-256 승인 키로 구성되고 원문 키를 포함하지 않는지 확인
- Redis `SET NX EX` 성공 결과를 lease로 반환하는 단위 테스트 성공
- H2 Flyway V2에서 `payment_settlements` 테이블 생성 확인
- 승인 성공 시 정산 원장 snapshot과 `SETTLEMENT_REQUESTED` outbox event 생성 확인
- 핵심 단위 테스트와 승인 통합 테스트 성공
- H2 전체 테스트 67개 성공, 실패 0개, 오류 0개, skipped 0개

### Troubleshooting

- 첫 컴파일에서 outbox 테스트용 no-op ledger가 중복 선언되어 `Redeclaration` 오류가 발생했습니다. 중복 선언을 제거했습니다.
- 정산 서비스가 계산기 함수의 실제 인자명과 다른 `grossAmount`를 사용해 컴파일 오류가 발생했습니다. 기존 함수 시그니처인 `amount`로 맞췄습니다.
- 전체 Spring 컨텍스트 테스트에서 `SettlementAmountCalculator`가 Spring bean으로 등록되지 않아 33개 테스트가 함께 실패했습니다. 상태가 없는 계산기를 `@Component`로 등록한 뒤 전체 테스트가 통과했습니다.

### Constraints

- Redis 실제 연결과 MySQL V3 migration은 Compose 환경에서 검증했습니다. 다중 애플리케이션 인스턴스 부하 측정은 후속 범위입니다.
- Kafka와 외부 Toss API, 실결제, 유료 서비스는 호출하지 않았습니다.

## 2026-07-26 (로컬 부하 테스트·Docker Compose 구성)

### Validation

- Node 내장 `fetch` 기반 `scripts/load-test.mjs` 추가
- 요청 수, 동시성, 기대 상태 코드, 평균·p50·p95·실패율 출력 구조 확인
- `node --check scripts/load-test.mjs` 성공
- MySQL 8.0·Redis 7.2 Compose healthcheck와 로컬 포트 설정 추가
- CI에 `docker compose config --quiet`와 부하 테스트 syntax check 추가
- 기본 H2 환경에서 100건·동시성 10 cursor 조회 부하 측정: HTTP 200 100건, 실패 0건, 평균 71.04ms, p50 14.26ms, p95 562.78ms

### Constraints

- 측정 결과는 기본 H2·단일 로컬 실행 조건에 한정하며, 운영 latency나 처리량으로 일반화하지 않았습니다.
- 측정 중 PowerShell `Start-Process`의 stdout·stderr를 같은 파일로 지정해 서버가 시작되지 않는 오류가 발생했습니다. 로그 파일을 분리한 뒤 재실행했고, 측정 후 8080 포트를 정리했습니다.
- Docker Compose 실제 기동, Redis 연결, MySQL migration 검증을 완료했습니다.
- GitHub Actions workflow는 로컬에서 실행하지 않았고 push하지 않았습니다.

### Compose 포트 충돌 조치

- 기존 `payment-core-mysql` 컨테이너가 호스트 `3307`을 사용해 Compose MySQL 시작이 실패했습니다.
- 기존 컨테이너를 중지하지 않고 `compose.yaml`의 MySQL 호스트 포트를 `3308`로 변경해 재기동했습니다.

### Compose 실제 검증 결과

- `docker compose config --quiet` 성공
- `payment-core-mysql-compose`: MySQL 8.0, healthy, host `3308`
- `payment-core-redis-compose`: Redis 7.2, healthy, host `6379`
- Flyway history: V1 `initial schema`, V2 `preserve local datetime precision`, V3 `payment settlement ledger` 모두 success
- Compose MySQL·Redis 연결과 Redis 활성화 설정으로 전체 테스트 67개 성공, 실패 0개, 오류 0개, skipped 0개
- Redis 임시 키 검증: 첫 `SET NX EX`는 `OK`, 두 번째 중복 요청은 차단, 삭제 결과 `1`
- 기존 `payment-core-mysql` 컨테이너는 중지하지 않았습니다.

## 2026-07-29 (Compose MySQL·Redis 재검증)

### Validation

- Docker Linux engine 응답 확인
- `payment-core-mysql-compose`: MySQL 8.0, healthy, host `3308`
- `payment-core-redis-compose`: Redis 7.2, healthy, host `6379`
- `docker compose config --quiet` 성공
- CI와 동일한 MySQL driver·JPA dialect·Flyway 위치·local gateway 환경변수로 전체 테스트 실행
- Compose MySQL·Redis 환경 전체 테스트 67개 성공, 실패 0개, 오류 0개, skipped 0개
- Redis 임시 키 검증: 첫 `SET NX EX`는 `OK`, 두 번째 요청은 응답 없이 차단, 기존 `lease-token-1` 보존 확인, 삭제 결과 `1`

### Troubleshooting

- 첫 로컬 `prod` 테스트 실행에서 `DB_DRIVER_CLASS`와 `JPA_DIALECT`를 전달하지 않아 기본 H2 드라이버가 MySQL JDBC URL에 적용되었습니다.
- 실제 오류는 `Driver org.h2.Driver claims to not accept jdbcUrl, jdbc:mysql://localhost:3308/payment_core...`였고, Spring context 초기화 실패로 67개 중 34개가 실패했습니다.
- Docker·MySQL 문제가 아니라 실행 명령의 환경변수 누락이 원인이므로 코드나 migration은 변경하지 않고 CI와 동일한 환경변수를 포함해 재실행했습니다.
- README의 Compose 실행 예시에 누락된 `DB_DRIVER_CLASS`, `JPA_DIALECT`, MySQL Flyway 위치와 테스트용 provider·worker 설정을 추가했습니다.

### Constraints

- 외부 Toss API, 실결제, Kafka, 클라우드 DB, 유료 서비스는 사용하지 않았습니다.
- Compose 컨테이너는 현재 검증을 위해 실행 중이며, 기존 작업 트리의 변경사항은 커밋·push하지 않았습니다.

## 2026-07-29 (NEXT-36 prod 데이터소스 설정 하드닝)

### Validation

- `git diff --check` 성공
- 기본 H2 전체 테스트 67개 성공, 실패 0개, 오류 0개, skipped 0개
- `prod` 프로파일에서 DB URL·계정만 지정하고 `DB_DRIVER_CLASS`, `JPA_DIALECT`는 지정하지 않은 상태로 Compose MySQL·Redis 전체 테스트 실행
- `prod` 전체 테스트 67개 성공, 실패 0개, 오류 0개, skipped 0개
- Docker Compose MySQL·Redis 컨테이너는 모두 healthy 상태 유지

### Troubleshooting

- 앞선 `prod` 검증에서 MySQL URL에 H2 드라이버가 선택되는 설정 불일치를 실제로 확인했습니다.
- 원인은 `application.yml`의 H2 기본값이 `application-prod.yml`에서 드라이버·JPA 방언까지 재정의되지 않았고, 실행 명령에서도 관련 환경변수가 누락된 것이었습니다.
- 실행 명령마다 환경변수를 추가하는 방법도 가능했지만, 프로파일 자체의 계약을 명확히 하는 편이 재현성과 운영 안전성에 유리하다고 판단했습니다.
- `prod`에 DB URL·계정 필수값, MySQL 드라이버·방언 기본값을 추가한 뒤 동일 조건으로 재검증했습니다.

### Constraints

- 실제 Toss API, 실결제, Kafka, 클라우드 DB, 유료 서비스는 사용하지 않았습니다.
- GitHub Actions는 push·실행하지 않았고, 기존 작업 트리 변경사항도 커밋·push하지 않았습니다.

## 2026-07-29 (NEXT-37 Toss gateway 장애 분류 회귀 테스트)

### Validation

- Toss gateway 단위 테스트에서 4xx 응답의 거절 변환과 무재시도 확인
- Toss gateway 단위 테스트에서 timeout의 설정 횟수 재시도와 일시 장애 변환 확인
- H2 전체 테스트 69개 성공, 실패 0개, 오류 0개, skipped 0개
- Compose MySQL·Redis `prod` 전체 테스트 69개 성공, 실패 0개, 오류 0개, skipped 0개
- Docker Compose MySQL·Redis 컨테이너 healthy 상태 유지 확인

### Troubleshooting

- 첫 timeout 테스트 작성 시 `MockRestResponseCreators.withException`에 `ResourceAccessException`을 직접 전달해 컴파일 오류가 발생했습니다.
- 실제 컴파일 메시지는 `Type mismatch: inferred type is ResourceAccessException but IOException was expected`였습니다.
- 테스트 helper가 네트워크 원인 예외인 `IOException`을 받아 Spring이 `ResourceAccessException`으로 감싸는 구조를 확인한 뒤, `IOException("timeout")`을 전달하도록 수정했습니다.
- 별도 네트워크 호출이나 테스트 helper 우회는 사용하지 않았고, 수정 후 단위 테스트와 전체 테스트를 다시 실행했습니다.

### Constraints

- 실제 Toss API·secret key·실결제·Kafka·클라우드 DB·유료 서비스는 사용하지 않았습니다.
- GitHub Actions는 push·실행하지 않았고, 기존 작업 트리 변경사항은 커밋·push하지 않았습니다.

## 2026-07-29 (NEXT-38 ~ NEXT-44 운영 준비 최종 검증)

### Validation

- H2 전체 테스트: 74개 성공, 실패 0개, 오류 0개, skipped 0개
- Compose MySQL·Redis `prod` 전체 테스트: 74개 성공, 실패 0개, 오류 0개, skipped 0개
- `docker compose config --quiet` 성공
- Docker multi-stage image build 성공
- `docker compose up -d --build` 후 API·MySQL·Redis 컨테이너 running/healthy 확인
- 컨테이너 `GET /api/v1/health`: HTTP 200, `status=ok`, `database=up`, `redis=up`
- local gateway 결제 생성·승인·동일 승인 멱등키 재요청 smoke 성공
- Dashboard `npm run build` 성공

### NEXT-39 MySQL 측정

- 조건: Compose MySQL, 800건 적재, page size 20, offset/cursor 각 60회
- Offset: 평균 67.07ms, p95 83.10ms, min 48.04ms, max 201.82ms
- Cursor: 평균 63.46ms, p95 75.68ms, min 49.45ms, max 84.25ms
- `EXPLAIN`에서 offset은 index scan, cursor는 `id < cursorId` range 조건을 확인했습니다.
- 단일 로컬 측정이므로 운영 latency·처리량으로 일반화하지 않습니다.

### NEXT-40 worker 검증

- worker fixed delay 500ms로 pending outbox 800건을 처리했습니다.
- 시작 시 pending 800·published 1·retrying 0·failed 0
- 처리 후 pending 0·published 801·retrying 0·failed 0
- 외부 broker 없이 local publisher 경계만 검증했습니다.

### Troubleshooting

- Redis 선행 guard 적용 후 첫 컴파일에서 `PaymentService.kt`의 `payment` 반환값이 try 블록 밖에 남아 `Unresolved reference: payment` 오류가 발생했습니다. 응답 반환을 lease가 유효한 try 블록 안으로 이동한 뒤 `PaymentServiceTest`와 전체 테스트를 재실행했습니다.
- 기존 worker 검증 프로세스를 정리할 때 PowerShell의 자동 변수 `$PID`와 작업 변수명이 충돌해 종료 명령이 실패했습니다. 변수명을 `$backendPidNext40`으로 바꿔 대상 Java PID를 명시한 뒤 종료했습니다.
- health endpoint 최초 구현에서 `Connection::isValid`를 함수 참조로 전달해 `Type mismatch` 컴파일 오류가 발생했습니다. JDBC 계약에 맞게 `isValid(2)`를 직접 호출하도록 수정했습니다.
- Docker 첫 이미지 빌드에서 Linux 컨테이너가 `./gradlew: not found`로 종료되었습니다. Windows 작업 트리의 CRLF shebang이 원인이었고, Dockerfile build 단계에서 wrapper 줄바꿈을 정규화한 뒤 이미지 빌드가 성공했습니다.
- prod 전체 테스트 강제 재실행에서 health 테스트 1건이 Redis disabled를 기대했지만 prod 환경변수는 Redis enabled여서 실패했습니다. 테스트가 의도한 disabled 시나리오를 명확히 하도록 테스트 전용 property를 고정하고 다시 실행해 74개 전체 성공을 확인했습니다.
- 처음 실행한 prod 테스트는 Gradle이 `UP-TO-DATE`로 처리해 실제 실행되지 않았습니다. 결과를 성공으로 기록하지 않고 `--rerun-tasks`로 강제 재실행했습니다.

### Constraints

- 실제 Toss API·secret key·실결제·Kafka·클라우드 DB·유료 외부 서비스는 사용하지 않았습니다.
- GitHub Actions 실행과 GitHub push는 하지 않았습니다.

## 2026-08-02 (NEXT-45 ~ NEXT-47 OpenAPI 계약 정리)

### Validation

- `PaymentControllerIntegrationTest` 실행 결과: 35개 성공, 실패 0개, 오류 0개, skipped 0개
- `/api-docs` 응답의 title `Kotlin Payment Core API` 확인
- `/api-docs` 응답의 version `v1` 확인
- 결제 목록·승인·아웃박스 재시도 path와 operation summary 확인
- Docker·실제 Toss API·실결제·Kafka·유료 외부 서비스는 사용하지 않음

### Troubleshooting

- 작업 시작 시 저장소 읽기 명령이 Windows `CreateProcessAsUserW failed: 5 (액세스가 거부되었습니다.)`로 실행되지 않았습니다. 우회하지 않고 읽기 전용 권한을 요청한 뒤 동일 점검을 재실행했습니다.

### Decision

OpenAPI 전체 JSON을 문자열로 고정하지 않고 핵심 경로만 회귀 검증했습니다. 문서 설명은 controller tag와 operation metadata에 두고, 상세 선택 근거는 `docs/adr/2026-08-02-openapi-contract.md`로 분리했습니다.

## 2026-08-02 (NEXT-50 최종 완료 검증)

### Validation

- H2 전체 테스트: 75개 성공, 실패 0개, 오류 0개, skipped 0개
- Dashboard `npm run build`: Vite production build 성공
- `git diff --check`: 오류 없음
- Docker는 이번 최종 검증에서 사용하지 않았고, 실제 Toss API·실결제·Kafka·유료 외부 서비스도 호출하지 않았습니다.

### Completion Decision

기능 구현과 로컬 검증 기준을 충족했으므로 현재 작업 트리를 release candidate로 취급합니다. 이후에는 실제 오류 수정과 검증 보강만 반영하고, Kafka·실제 Toss·Kubernetes·클라우드 배포는 별도 후속 범위로 유지합니다.
