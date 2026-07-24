# ADR: Toss 결제 취소 gateway 경계

- 상태: Accepted
- 날짜: 2026-07-24
- 범위: 승인된 결제의 전액 취소 처리

## 배경

기존 취소 API는 내부 결제 상태만 `CANCELED`로 바꾸고 외부 PG 취소 결과를 확인하지 않았습니다. 실제 Toss 연동에서는 승인 결과의 `paymentKey`로 취소 endpoint를 호출하고, PG 취소 성공 이후에만 내부 상태를 전환해야 합니다.

## 결정

- `PaymentGateway`에 취소 요청 경계를 추가합니다.
- Toss adapter는 `POST /v1/payments/{paymentKey}/cancel`을 호출합니다.
- 요청 본문에는 기본 취소 사유를 포함하고, 취소 idempotency key를 요청 헤더로 전달합니다.
- Toss 4xx는 거절, 5xx·timeout은 제한된 재시도 후 일시 장애로 분류합니다.
- PG 취소가 거절되면 내부 결제는 `APPROVED`로 유지하고, 성공한 경우에만 `CANCELED`와 outbox 이벤트를 저장합니다.
- 기본 `local` provider는 외부 호출 없이 동일한 gateway 경계를 통과합니다.

## 대안과 선택 이유

1. 서비스에서 Toss HTTP를 직접 호출하는 방법
   - 결제 상태 전이와 외부 통신이 섞여 테스트·provider 교체가 어려워집니다.
2. 내부 상태를 먼저 `CANCELED`로 바꾸는 방법
   - PG 취소 실패 시 내부와 외부 상태가 어긋날 수 있습니다.
3. gateway 성공 후 상태를 전환하는 방법
   - 외부 결과를 내부 상태의 선행 조건으로 두고, local fake와 Toss adapter를 같은 서비스 흐름에서 검증할 수 있어 선택했습니다.

## 제한과 다음 단계

- 현재 API는 전액·부분 취소와 기본 취소 사유를 지원합니다. 가상계좌 환불 계좌와 결제수단별 취소 필드는 별도 request 모델로 확장해야 합니다.
- 실제 Toss secret key와 실결제 endpoint는 사용하지 않았으며, 운영에서는 취소 사유와 idempotency key를 호출 맥락에 맞게 전달해야 합니다.
