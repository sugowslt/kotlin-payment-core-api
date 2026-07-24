# 외부 PG 승인 경계 결정

## 배경

결제 승인 서비스가 특정 PG SDK나 HTTP 클라이언트에 직접 의존하면 PG 교체, 테스트, 장애 분류가 어려워집니다. 실제 Toss API를 붙이기 전에 승인 도메인과 외부 PG 호출의 책임을 분리할 필요가 있습니다.

## 결정

`PaymentGateway` 인터페이스를 승인 서비스와 외부 PG 사이에 두고, 현재는 `LocalPaymentGateway`를 기본 구현으로 사용합니다.

승인 결과에서 받은 `providerTransactionId`는 결제에 저장하고, 외부 PG 오류는 다음 두 종류로 분류합니다.

- `PaymentGatewayRejectedException`: PG가 결제를 명확히 거절한 경우. 결제를 `FAILED`로 기록하고 `402 PAYMENT_GATEWAY_REJECTED`를 반환합니다.
- `PaymentGatewayUnavailableException`: 타임아웃·일시적 장애처럼 결과를 확정할 수 없는 경우. 결제 상태를 `PENDING`으로 유지하고 `503 PAYMENT_GATEWAY_UNAVAILABLE`을 반환해 재시도 가능성을 보존합니다.

## 선택 이유

- 도메인 서비스가 특정 PG의 요청·응답 모델에 묶이지 않습니다.
- 로컬 fake gateway로 외부 네트워크 없이 성공·거절·일시 장애를 재현할 수 있습니다.
- 결제 결과가 확정된 실패와 미확정 실패를 구분해 재시도 정책을 다르게 가져갈 수 있습니다.

## 현재 범위와 한계

현재 구현은 실제 Toss API를 호출하지 않습니다. 인증 키, `paymentKey` 매핑, HTTP 타임아웃·재시도 클라이언트, 웹훅은 다음 단계에서 실제 어댑터와 함께 추가해야 합니다.

또한 외부 PG 호출은 현재 승인 트랜잭션 안에서 수행하는 동기 경계입니다. 운영 환경에서는 잠금 유지시간과 외부 호출 지연을 측정한 뒤, 승인 중간 상태·아웃박스·보정 작업으로 분리할지 결정해야 합니다.
