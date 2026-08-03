# ADR: API 계약을 OpenAPI 메타데이터와 회귀 테스트로 고정

## 상태

Accepted

## 배경

springdoc 의존성과 `/api-docs` endpoint는 이미 있었지만, controller별 책임과 핵심 상태 전이가 문서에서 충분히 드러나지 않았습니다. 포트폴리오를 읽는 사람이 실제 구현을 열기 전에 결제 생명주기와 운영 endpoint의 경계를 확인할 수 있어야 했습니다.

## 결정

- Payments, Toss Webhooks, Internal Webhook Operations, Internal Outbox Operations, Internal Audit로 OpenAPI tag를 나눕니다.
- 승인·취소·cursor 조회·웹훅 replay·아웃박스 재시도 등 핵심 endpoint에 요약과 처리 의도를 기록합니다.
- 문서 제목·버전·설명은 애플리케이션 설정 bean으로 고정합니다.
- `/api-docs` 응답에서 문서 제목, 버전, 결제 목록, 승인, 아웃박스 재시도 경로를 통합 테스트로 검증합니다.

## 선택 이유

주석만 추가하면 문서가 깨져도 테스트가 알아채지 못합니다. 반대로 API JSON의 핵심 경로를 테스트하면 springdoc 설정 변경이나 controller mapping 변경을 빠르게 발견할 수 있습니다. 전체 schema를 문자열로 고정하지 않고 포트폴리오 설명에 필요한 핵심 계약만 검증해 테스트가 구현 세부사항에 과도하게 결합되지 않도록 했습니다.

## 검증

- `PaymentControllerIntegrationTest`: 35개 성공, 실패 0개, 오류 0개, skipped 0개
- OpenAPI title: `Kotlin Payment Core API`
- OpenAPI version: `v1`
- 결제 목록·승인·아웃박스 재시도 경로와 operation summary 확인

## 제약

이번 변경은 문서 계약 정리이며 실제 Toss API, 실결제, Kafka, 클라우드 DB, 유료 서비스는 사용하지 않았습니다.
