# Toss 결제 웹훅 보정 처리 결정

## 배경

승인 API 응답만으로 결제 상태를 확정하면 외부 PG와 내부 DB 사이의 상태 불일치를 복구하기 어렵습니다. Toss의 `PAYMENT_STATUS_CHANGED` 이벤트를 받아 상태를 보정하고, 같은 웹훅이 재전송되어도 중복 처리하지 않아야 합니다.

## 결정

- `tosspayments-webhook-transmission-id`를 `payment_webhook_events` 테이블에 unique 저장합니다.
- 같은 transmission ID가 다시 들어오면 `DUPLICATE`로 응답하고 상태 변경을 반복하지 않습니다.
- 결제 조회·변경에는 결제 행 비관적 쓰기 잠금을 사용합니다.
- 상태 매핑은 다음과 같이 제한합니다.
  - `DONE` + `PENDING` → `APPROVED`
  - `EXPIRED`/`ABORTED` + `PENDING` → `FAILED`
  - `CANCELED` + `APPROVED` → `CANCELED`
  - 나머지 조합 → 상태 변경 없이 기록

## 선택 이유

- transmission ID unique 제약과 처리 결과 기록을 함께 두어 재전송을 추적할 수 있습니다.
- 상태 보정과 중복 방지 판단을 같은 트랜잭션 안에서 수행할 수 있습니다.
- 이미 확정된 상태를 늦게 도착한 웹훅이 되돌리지 않도록 허용 가능한 전이만 적용합니다.

## 보안과 한계

현재 대상은 결제 상태 웹훅입니다. Toss 공식 문서상 `tosspayments-webhook-signature`는 현재 `payout.changed`, `seller.changed` 웹훅에 제공되며 결제 상태 웹훅에는 포함되지 않습니다. 따라서 이 구현은 transmission ID 중복 방지와 상태 전이에 집중하고, 운영 배포 전에는 Toss가 제공하는 네트워크·방화벽 설정과 운영 접근 제어를 별도로 적용해야 합니다.

실제 운영에서는 이벤트 원문 보관 정책, 알 수 없는 주문 처리, 웹훅 재처리 도구, 보정 결과 모니터링을 추가로 설계해야 합니다.
