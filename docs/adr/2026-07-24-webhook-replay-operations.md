# 웹훅 재처리 운영 기능 결정

## 배경

웹훅이 결제 생성보다 먼저 도착하거나 일시적인 DB 문제로 상태 보정이 완료되지 않으면, PG 재전송만 기다리는 방식으로는 운영자가 즉시 복구하기 어렵습니다. 수신 원문과 처리 결과를 기준으로 안전한 재처리와 최소한의 운영 지표가 필요합니다.

## 결정

- 웹훅 원문 JSON을 `payment_webhook_events.payload`에 저장합니다.
- `IGNORED_PAYMENT_NOT_FOUND` 등 미처리 이벤트는 transmission ID로 재처리할 수 있습니다.
- 이미 `PROCESSED_` 또는 `REPROCESSED_` 상태인 이벤트는 `ALREADY_PROCESSED`로 반환해 중복 보정을 막습니다.
- 재처리와 지표 endpoint는 `PAYMENT_WEBHOOK_REPLAY_TOKEN`과 `X-Webhook-Replay-Token`으로 보호합니다.
- 지표는 전체·처리·재처리·무시 이벤트 수만 제공하며, 외부 SaaS로 전송하지 않습니다.

## 선택 이유

- 원문 저장으로 운영자가 동일 이벤트를 재현할 수 있습니다.
- 전송 ID와 처리 결과를 함께 보존해 재처리 이력을 추적할 수 있습니다.
- 기본 replay token이 비어 있으면 운영 endpoint를 비활성화해 실수로 공개되는 것을 막습니다.

## 한계

현재 보호 방식은 프로젝트 학습용 내부 token입니다. 운영 환경에서는 관리자 인증·권한, 감사 로그, rate limit, 재처리 승인 절차를 추가해야 합니다. payload에는 민감정보가 포함될 수 있으므로 실제 운영에서는 암호화·보관기간·마스킹 정책을 별도로 정해야 합니다.
