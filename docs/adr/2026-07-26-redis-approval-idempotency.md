# Redis 승인 멱등성 보조 계층

## 상태

수용. 실제 Redis 컨테이너 검증은 별도 환경에서 진행한다.

## 맥락

승인 요청은 DB의 승인 키와 비관적 잠금으로 정합성을 보장하고 있다. 다만 여러 애플리케이션 인스턴스가 같은 키로 동시에 들어오면 DB lock을 기다리기 전에 빠르게 중복 진입을 줄일 수 있는 분산 게이트가 필요하다.

## 결정

- Redis `SET NX EX`를 승인 키별 보조 게이트로 사용한다.
- Redis key에는 결제 ID와 SHA-256 digest만 넣어 원문 승인 키를 저장하지 않는다.
- lease에는 무작위 token을 넣고, 해제 시 Redis script에서 token이 같은 경우에만 삭제한다.
- Redis는 최종 정합성 저장소가 아니다. 승인 행 잠금과 `approval_idempotency_key`를 최종 기준으로 유지한다.
- Redis 연결 장애는 승인 전체 장애로 확대하지 않고 DB 경로로 fallback한다. 단, Redis가 정상이고 같은 요청 lease가 이미 있으면 `409 PAYMENT_IDEMPOTENCY_IN_PROGRESS`를 반환한다.
- lease TTL은 프로세스 장애 시 고아 lock을 회수할 수 있도록 설정값으로 둔다.

## 대안

- DB만 사용: 정합성은 충분하지만 모든 중복 요청이 DB lock 경합으로 들어간다.
- Redis만 사용: 빠르지만 Redis 유실·만료와 DB 상태 불일치 위험이 있어 최종 기준으로 채택하지 않았다.
- 분산 lock 라이브러리 사용: 현재 범위에 비해 운영·의존성이 커서 단순 `SET NX EX` 포트부터 적용했다.

## 설정

- `PAYMENT_IDEMPOTENCY_REDIS_ENABLED=false` 기본값
- `REDIS_URL=redis://localhost:6379`
- `PAYMENT_IDEMPOTENCY_REDIS_TTL_SECONDS=30`
