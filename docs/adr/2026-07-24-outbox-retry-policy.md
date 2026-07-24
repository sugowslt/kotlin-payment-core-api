# ADR: 트랜잭션 아웃박스 재시도와 최종 실패 정책

- 상태: Accepted
- 날짜: 2026-07-24
- 범위: 로컬 아웃박스 publisher의 실패 처리

## 배경

기존 로컬 `publish`는 대기 이벤트를 성공으로만 전환했습니다. 외부 publisher가 일시적으로 실패하는 상황을 설명하려면 재시도 횟수, 다음 시도 시각, 최종 실패 상태가 필요합니다.

## 결정

- publisher 호출 성공 시 이벤트를 `PUBLISHED`로 전환하고 `publishedAt`을 기록합니다.
- publisher 호출 실패 시 `retryCount`를 1 증가시키고, 최대 재시도 전이면 `PENDING`으로 유지합니다.
- 재시도 전 대기 시간은 `PAYMENT_OUTBOX_RETRY_DELAY_SECONDS`로 설정합니다.
- `PAYMENT_OUTBOX_MAX_RETRIES`에 도달하면 `FAILED`로 전환하고 `lastError`를 보존합니다.
- 재시도 대상은 `nextAttemptAt`이 현재 시각 이전인 `PENDING` 이벤트 중 생성 순서 상위 50개입니다.
- 현재 publisher는 로컬 컴포넌트이며 외부 Kafka나 비용이 발생하는 서비스는 연결하지 않습니다.

## 대안과 선택 이유

1. 실패 이벤트를 즉시 `FAILED`로 고정하는 방법
   - 원인 복구 후 자동 재처리가 불가능해 일시 장애와 영구 실패를 구분하기 어렵습니다.
2. 실패 이벤트를 계속 `PENDING`으로만 두는 방법
   - 무한 재시도로 장애를 확대할 수 있고 운영자가 최종 실패를 식별하기 어렵습니다.
3. 제한된 재시도 후 `FAILED`로 전환하는 방법
   - 일시 장애에는 자동 복구 기회를 주면서, 최대 시도 이후에는 명확한 운영 대상이 되므로 선택했습니다.

## 제한과 다음 단계

- 실제 broker publish, exponential backoff, consumer offset, 최종 실패 알림은 아직 구현하지 않았습니다.
- 외부 broker를 도입할 때도 현재 outbox 상태와 retry 기록을 발행 기준으로 유지하고, publisher의 실제 오류 분류를 추가 검토합니다.
