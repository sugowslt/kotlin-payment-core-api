# ADR: 아웃박스 자동 worker와 동시 실행 방지

- 상태: Accepted
- 날짜: 2026-07-24
- 범위: `PENDING` 아웃박스 이벤트의 자동 발행 경계

## 배경

기존 아웃박스 처리는 운영 endpoint를 호출해야만 실행되었습니다. 실제 서비스에서는 주기적인 publisher worker가 필요하고, 여러 인스턴스가 동시에 실행되어도 같은 이벤트를 중복 발행하지 않아야 합니다.

## 결정

- Spring scheduled worker를 추가하고 `PAYMENT_OUTBOX_WORKER_ENABLED`로 활성화합니다.
- worker 기본값은 `false`로 두어 로컬 대시보드의 수동 시연과 충돌하지 않게 합니다.
- 실행 주기는 `PAYMENT_OUTBOX_WORKER_FIXED_DELAY_MS`로 설정합니다.
- outbox 대상 조회에 `PESSIMISTIC_WRITE` 잠금을 적용해 한 트랜잭션이 이벤트를 처리하는 동안 다른 worker가 같은 행을 가져가지 못하게 합니다.
- worker는 기존 `PaymentOutboxService.publishPending()`을 호출하므로 재시도·최종 실패 정책을 별도로 복제하지 않습니다.

## 대안과 선택 이유

1. worker마다 메모리 lock을 두는 방법
   - 단일 JVM에서만 동작하고 다중 인스턴스 환경의 중복 처리를 막지 못합니다.
2. 외부 분산 lock 서비스를 바로 도입하는 방법
   - 운영 구조에는 도움이 되지만 현재 로컬·무비용 검증 범위를 불필요하게 확장합니다.
3. DB 행 잠금과 기존 outbox transaction을 사용하는 방법
   - 현재 H2/MySQL에서 재현 가능하고 별도 인프라 없이 여러 worker의 경합을 제어할 수 있어 선택했습니다.

## 제한과 다음 단계

- 현재 worker는 실제 외부 broker 대신 local publisher를 호출합니다.
- 운영 환경에서는 broker consumer와 publisher의 delivery guarantee, lock 대기 시간, 장애 알림을 별도로 검토합니다.
