# Project1 Domain Scope (Payment Core API)

## 1) 도메인 선택
- 선택 도메인: Payment
- 선택 이유:
  - 트랜잭션 무결성, 상태 전이, 실패 복구 등 백엔드 핵심 역량을 보여주기 좋음
  - 이후 Redis 캐시/락, Kafka 이벤트 확장과 연결하기 쉬움

## 2) 범위(In Scope)
- 결제 생성(Create Payment)
- 결제 상태 조회(Get Payment)
- 결제 취소(Cancel Payment)
- 결제 목록 조회(List Payments, 페이징)

## 3) 범위 밖(Out of Scope, Week1)
- 실제 PG 연동
- 정산 배치
- 다중 통화 처리

## 4) 핵심 유스케이스 3개
1. 결제 생성
- 사용자 주문 기준으로 결제 요청을 생성하고 초기 상태를 PENDING으로 저장

2. 결제 승인 처리
- 승인 이벤트/요청에 따라 상태를 APPROVED로 전이
- 승인 시 승인시각, 승인코드 기록

3. 결제 취소 처리
- APPROVED 상태 결제를 취소 요청해 CANCELED로 전이
- 취소 사유와 취소시각 기록

## 5) 비기능 요구사항 2개
1. 성능
- 결제 단건 조회 API p95 응답시간 200ms 이하(로컬 기준)

2. 안정성
- 동일 idempotency key로 중복 결제 생성 시 1건만 생성되고 나머지는 충돌 처리

## 6) API 초안(Week1)
- POST /payments
- GET /payments/{paymentId}
- POST /payments/{paymentId}/approve
- POST /payments/{paymentId}/cancel
- GET /payments?page=0&size=20

## 7) 설계 검증 포인트
- 설계 의사결정: 결제 상태를 단순 boolean 대신 상태 머신(enum)으로 관리
- 실패 처리 전략: 중복 요청 방지를 idempotency key 기반으로 설계
