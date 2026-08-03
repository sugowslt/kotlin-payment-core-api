# Flyway와 Docker 검증 중 발생한 오류 기록

## 상황

2026-07-26에 Hibernate의 `create-drop` 의존을 제거하고 Flyway V1 migration과 `ddl-auto=validate`를 적용했습니다. 기본 H2 전체 테스트를 먼저 실행한 뒤, 동일 migration의 MySQL 검증을 위해 로컬 `payment-core-mysql` 컨테이너를 사용하려고 했습니다.

## 오류 1: H2 schema validation 타입 불일치

첫 실행에서는 Flyway migration이 적용된 뒤 Spring context 초기화가 실패했습니다.

```text
Schema-validation: wrong column type encountered in column [payload]
in table [payment_outbox_events]; found [character varying (Types#VARCHAR)],
but expecting [text (Types#CLOB)]
```

### 원인

`PaymentOutboxEvent.payload`와 `PaymentWebhookEvent.payload`는 JPA `@Lob` 매핑으로 CLOB을 요구합니다. 초기 migration에서 두 컬럼을 `TEXT`로 작성했지만, 현재 H2 MySQL 호환 모드에서는 해당 타입이 검증 시 VARCHAR로 인식되어 JPA의 CLOB 기대값과 달라졌습니다.

### 검토한 대안

1. `ddl-auto=update`로 되돌리기
   - validation 오류는 숨길 수 있지만 운영 schema가 애플리케이션 시작 시 암묵적으로 변경되어 선택하지 않았습니다.
2. 모든 DB에서 `CLOB` 사용
   - H2는 맞지만 MySQL의 실제 문자열 타입은 TEXT 계열이므로 DB 호환성을 확인하지 못한 상태에서 공통 SQL로 유지하지 않았습니다.
3. DB별 Flyway migration 위치 분리
   - H2는 `CLOB`, MySQL은 `TEXT`를 사용하고 각 환경의 실제 타입을 명시적으로 검증할 수 있어 선택했습니다.

### 조치

- `db/migration/h2/V1__initial_schema.sql`의 payload 컬럼을 `CLOB`으로 작성했습니다.
- `db/migration/mysql/V1__initial_schema.sql`의 payload 컬럼은 MySQL `TEXT`로 작성했습니다.
- 기본 설정은 H2 위치를 사용하고 `prod` 프로파일은 MySQL 위치를 사용하도록 변경했습니다.
- 수정 후 H2 전체 테스트 56개가 성공했습니다.

## 오류 2: Docker Desktop startup crash-loop

MySQL 검증을 시작하기 위해 Docker Desktop을 실행했지만 다음 오류가 반복됐습니다.

```text
starting services: initializing Inference manager:
listening on unix://C:\Users\user\AppData\Local\Docker\run\dockerInference:
remove ...: The file cannot be accessed by the system.
```

stale `dockerInference` 소켓을 정리한 뒤에는 다음 단계에서 같은 유형의 오류가 확인됐습니다.

```text
starting services: initializing Secrets Engine:
listening on unix://C:\Users\user\AppData\Local\docker-secrets-engine\engine.sock:
remove ...: The file cannot be accessed by the system.
```

### 확인한 사실

- Docker Desktop backend가 named pipe를 만들기 전에 초기화 오류로 종료됐습니다.
- 두 소켓은 `ReparsePoint`인 0바이트 stale runtime socket이었고, 확인 시 Docker 프로세스가 점유하고 있지 않았습니다.
- Docker 공식 `docker desktop disable model-runner` 명령은 backend IPC가 이미 내려간 상태라 `dockerBackendApiServer` 연결 오류로 실행되지 않았습니다.
- 정확한 stale socket 두 개를 각각 정리했지만 재시작 시 동일한 초기화 crash-loop가 재현됐습니다.
- `EnableDockerAI`를 임시로 `false`로 변경해 확인했으나 오류가 계속되어 원래 값 `true`로 복원했습니다.

### 선택하지 않은 대안

- Docker Desktop 공장 초기화: 기존 컨테이너·이미지·볼륨에 영향을 줄 수 있어 실행하지 않았습니다.
- `wsl --shutdown`: 다른 WSL 작업과 프로젝트에 영향을 줄 수 있어 실행하지 않았습니다.
- Docker Desktop 재설치·업데이트: 현재 범위를 넘어서는 시스템 변경이고 추가 다운로드가 필요해 실행하지 않았습니다.
- 외부 MySQL·클라우드 DB 사용: 비용 또는 외부 시스템 사용 가능성이 있어 실행하지 않았습니다.

## 오류 2-1: MySQL 테스트 명령의 JDBC URL 보간 오류

Docker Desktop이 정상화된 뒤 첫 MySQL 실행은 migration 이전에 다음 접속 오류로 실패했습니다.

```text
Access denied for user 'payment'@'%' to database '=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Seoul'
```

### 원인

컨테이너의 `MYSQL_DATABASE=payment_core` 설정은 정상이었습니다. 실행 명령을 PowerShell에서 조합할 때 `$mysqlDatabase?useSSL=false...`처럼 변수 뒤에 물음표를 바로 붙여 PowerShell이 변수 경계를 잘못 해석했고, 잘못된 JDBC URL이 Gradle 프로세스에 전달됐습니다.

### 조치

- 비밀값을 출력하지 않고 URL만 점검해 애플리케이션 코드가 아닌 실행 명령의 문제임을 확인했습니다.
- `String.Format`으로 database 이름과 query string을 분리해 URL을 생성했습니다.
- 수정 후 Flyway 접속과 migration이 정상적으로 진행됐습니다.

## 오류 3: MySQL timestamp 정밀도 차이

Docker Desktop이 정상화된 뒤 올바른 JDBC URL로 MySQL 테스트를 재실행하자 Flyway migration은 통과했지만 다음 테스트 하나가 실패했습니다.

```text
get payments returns correct sorting by created_at descending()
expected: 3000.0 but was: 1000.0
```

### 원인

초기 MySQL V1 migration의 `TIMESTAMP` 컬럼은 초 단위로 저장됐습니다. 테스트가 세 결제를 같은 초 안에 생성하면서 `created_at` 값이 같아졌고, `created_at DESC` 정렬만으로는 생성 순서를 보장할 수 없었습니다. H2에서는 같은 코드가 통과했지만 DB별 timestamp 정밀도가 달랐습니다.

### 조치

- 이미 적용된 V1 checksum을 수정하지 않고 MySQL 전용 `V2__preserve_local_datetime_precision.sql`을 추가했습니다.
- 결제·취소·웹훅·아웃박스·감사 이벤트의 `LocalDateTime` 컬럼을 `TIMESTAMP(6)`으로 변경합니다.
- 다음 실행에서 Flyway V2 적용과 전체 MySQL 테스트를 재검증합니다.

## 최종 검증

- H2: 56개 테스트 성공, 실패 0개, 오류 0개, skipped 0개
- MySQL: 56개 테스트 성공, 실패 0개, 오류 0개, skipped 0개
- 외부 Toss API, 실결제, 유료 서비스는 사용하지 않았습니다.

관련 참고:

- [Docker Desktop Model Runner 비활성화 CLI](https://docs.docker.com/reference/cli/docker/desktop/disable/model-runner/)
- [Docker Desktop Windows stale AF_UNIX socket startup crash-loop 이슈](https://github.com/docker/desktop-feedback/issues/436)
