# 내부 운영 API 보안·감사 로그

## Context

웹훅 재처리와 아웃박스 수동 처리는 일반 결제 API와 분리된 운영 기능입니다. 기존에는 두 컨트롤러가 같은 replay token 비교 로직을 각각 가지고 있었고, 누가 어떤 운영 변경을 실행했는지 DB에서 확인할 수 없었습니다. 로컬 대시보드에서 운영 흐름을 확인할 수 있는 만큼, 운영 API의 인증 규칙과 변경 이력을 한 곳에서 확인할 수 있어야 합니다.

## Decision

- `InternalOperationsAuthorizer`가 `X-Webhook-Replay-Token`을 constant-time 비교합니다.
- replay token이 비어 있거나 요청 token이 없으면 운영 endpoint를 허용하지 않습니다.
- 성공·실패가 결과를 바꾸는 내부 작업만 `internal_operation_audit_events`에 기록합니다.
  - `OUTBOX_PUBLISH`
  - `OUTBOX_RETRY`
  - `WEBHOOK_REPLAY`
- 감사 이벤트에는 작업명, 대상 식별자, 결과, traceId, 오류 요약, 생성 시각을 저장합니다.
- 감사 기록은 `REQUIRES_NEW` 트랜잭션으로 저장해 운영 작업이 실패해도 실패 이력이 남도록 합니다.
- 최근 50개 감사 이력은 같은 token으로 보호된 `/api/v1/internal/audit-events`에서 조회합니다.
- `prod` 프로파일은 JPA schema를 `validate`로 고정하고, Toss provider·outbox worker를 운영 기본값으로 사용합니다. replay token은 환경변수로만 주입하며 로컬 기본 token을 사용하지 않습니다.

## Alternatives

1. 컨트롤러마다 token 비교 로직 유지
   - 구현은 단순하지만 수정 누락과 보안 정책 불일치 가능성이 있습니다.
2. 애플리케이션 로그에만 운영 작업 기록
   - 별도 저장소 없이 시작할 수 있지만 traceId와 결과를 조회하는 운영 화면 기능을 만들기 어렵고 보존 정책이 로그 설정에 종속됩니다.
3. 외부 감사 SaaS 사용
   - 운영 확장성은 있지만 이 프로젝트의 로컬 검증 범위를 벗어나고 비용이 발생할 수 있어 선택하지 않았습니다.

## Consequences

- 내부 운영 endpoint 인증 정책이 한 클래스에 모여 유지보수가 쉬워집니다.
- 운영 화면이나 장애 분석에서 최근 수동 조작과 실패 원인을 traceId로 연결할 수 있습니다.
- 감사 테이블은 로컬 H2/MySQL schema에 포함되며, 운영 DB migration 도구를 도입할 때 별도 migration으로 옮겨야 합니다.
- token 자체는 저장하지 않으며, 인증 실패 요청도 감사 로그에 기록하지 않아 민감정보와 노이즈를 줄입니다.
