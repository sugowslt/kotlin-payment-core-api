# 정산 원장과 승인 후 이벤트 경계

## 상태

수용. 외부 broker 연결 전 단계에서는 DB outbox까지만 검증한다.

## 맥락

정산 금액을 계산만 하면 승인 후 재처리나 사후 검증 시 당시 적용된 원금·수수료율·반올림 결과를 복원하기 어렵다. 또한 승인 성공과 후속 정산 요청이 별도 저장되면 한쪽만 성공하는 문제가 생길 수 있다.

## 결정

- 승인 성공 트랜잭션에서 `payment_settlements`에 계산 snapshot을 저장한다.
- 같은 트랜잭션에서 `PAYMENT_APPROVED`와 `SETTLEMENT_REQUESTED` outbox event를 저장한다.
- 원금, fee rate bps, fee amount, settlement amount를 snapshot과 event payload에 함께 남긴다.
- 정산 snapshot의 초기 상태는 `REQUESTED`로 두고 후속 publisher/worker 상태는 다음 단계에서 확장한다.
- 현재 publisher는 local 구현이며 Kafka는 이번 단계에 추가하지 않는다.

## 대안

- 승인 API에서 정산 API를 동기 호출: 외부 장애가 승인 응답까지 전파되고 재처리가 어렵다.
- 계산 결과를 event payload에만 저장: 원장 조회와 중복 방지 기준이 약하다.
- Kafka direct publish: DB commit과 broker publish 사이의 dual-write 문제가 남아 기존 transactional outbox 경계보다 안전하지 않다.
