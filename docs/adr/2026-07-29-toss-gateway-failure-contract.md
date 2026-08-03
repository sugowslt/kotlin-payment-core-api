# ADR: Toss gateway 장애 분류 계약

- 상태: Accepted
- 날짜: 2026-07-29
- 범위: Toss 승인·취소 adapter의 HTTP 오류 분류와 재시도 검증

## 배경

외부 결제 gateway의 4xx 응답은 결제 거절로 확정할 수 있지만, timeout이나 5xx 응답은 외부에서 처리가 완료되었는지 알 수 없습니다. 두 상황을 같은 실패로 처리하면 재시도 가능성을 잃거나 이미 처리된 결제를 중복 호출할 위험이 있습니다.

## 결정

- Toss 4xx 응답은 `PaymentGatewayRejectedException`으로 분류하고 재시도하지 않습니다.
- Toss 5xx와 네트워크 timeout은 설정된 최대 재시도 횟수까지 다시 요청한 뒤 `PaymentGatewayUnavailableException`으로 분류합니다.
- 승인·취소 요청에는 기존 idempotency key를 유지해 재시도 요청의 중복 처리 위험을 낮춥니다.
- 실제 Toss API나 secret key 없이 `MockRestServiceServer`로 HTTP 계약을 검증합니다.

## 선택 이유

- 확정된 거절과 결과 미확정 장애를 내부 결제 상태 전이에서 다르게 처리할 수 있습니다.
- 실결제나 외부 비용 없이 요청 횟수·헤더·응답 분류를 반복 검증할 수 있습니다.
- adapter 외부의 `PaymentGateway` 추상화와 기존 서비스 오류 처리 경계를 유지합니다.

## 검증과 제한

- 4xx 거절 시 요청 1회만 발생하는지 확인했습니다.
- timeout은 설정된 1회 재시도 후 일시 장애로 변환되는지 확인했습니다.
- H2와 Compose MySQL·Redis `prod` 전체 테스트는 각각 69개 성공했습니다.
- 실제 Toss endpoint, 네트워크 상태, provider별 error code 정책은 별도 sandbox 승인 후 검증해야 합니다.
