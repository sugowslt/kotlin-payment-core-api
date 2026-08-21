# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Payment soft-delete(soft-delete) 및 deletePayment API
- 상태 전이 검증 + DTO 유효성 검증
- Offset vs Cursor(keyset) 비교 조회
- 승인 API `Idempotency-Key` 처리 및 동일 키 재요청 멱등 응답
- 승인 경로에 비관적 쓰기 잠금을 적용해 동시 승인 경합 제어
- MySQL 기준 서로 다른 승인 키의 동시 요청 통합 테스트 추가
- `PaymentGateway` 경계와 로컬 fake provider 추가
- Toss confirm HTTP adapter와 Basic 인증·타임아웃·재시도 설정 추가
- PG 승인 거절·일시 장애 오류 분류 및 응답 코드 추가
- Toss 결제 상태 웹훅 수신·중복 방지·상태 보정 처리 추가
- 웹훅 원문 재처리 endpoint와 보호된 처리 지표 endpoint 추가
- 결제 변경 이벤트 트랜잭션 아웃박스와 로컬 운영 대시보드 추가
- 아웃박스 publisher 실패 시 재시도·최종 실패 상태와 오류 기록 추가
- `FAILED` 아웃박스 이벤트 수동 재처리 endpoint와 대시보드 조작 추가
- Toss 결제 취소 gateway 경계와 HTTP adapter mock 검증 추가
- 결제 취소 API `Idempotency-Key` 저장·재사용·충돌 처리 추가
- 부분 취소 금액 검증과 `payment_cancellations` 취소 이력 저장 추가
- 설정 가능한 아웃박스 scheduled worker와 DB 행 잠금 기반 동시 실행 방지 추가
- 내부 운영 token 인증 공통화와 운영 작업 DB 감사 로그 추가
- `prod` 프로파일의 schema 검증·운영 provider·worker 기본값 분리
- Flyway V1 기반 H2/MySQL schema migration과 `ddl-auto=validate` 적용
- MySQL `LocalDateTime` 보존을 위한 `TIMESTAMP(6)` V2 migration 추가
- GitHub Actions에서 H2/MySQL 전체 테스트와 대시보드 production build 검증 추가
- basis point 수수료율 기반 정산 금액 계산 모듈과 반올림 정책 추가
- Redis `SET NX EX` 기반 승인 멱등성 보조 게이트와 DB fallback 경로 추가
- 승인 성공 시 정산 원장 스냅샷과 `SETTLEMENT_REQUESTED` 트랜잭션 아웃박스 이벤트 기록 추가
- Node 내장 fetch 기반 로컬 부하 테스트 스크립트와 평균·p50·p95·실패율 출력 추가
- MySQL 8.0·Redis 로컬 검증용 Compose 파일과 compose/부하 테스트 CI 정적 검사 추가
- 운영 대시보드에서 최근 감사 이력을 조회하는 화면 추가
- 백엔드 기능별 처리 과정을 실제 localhost API로 보여주는 프로젝트 Walkthrough 대시보드 추가
- Toss gateway의 4xx 거절·timeout 일시 장애 분류와 재시도 회귀 테스트 추가
- Redis 승인 guard 선행 처리와 운영 health endpoint 추가
- 내부 운영 token 길이 제한과 Docker multi-stage 실행 이미지 추가
- Compose API·MySQL·Redis 통합 실행과 최종 walkthrough 검증 기록 추가
- OpenAPI 문서 제목·버전·controller 책임별 tag와 핵심 operation 설명 추가
- `/api-docs` 결제 lifecycle·outbox retry 경로 회귀 테스트 추가
- 프로젝트 완료 기준과 제외 범위를 README에 정리
- 테스트 케이스 보강

### Changed
- README에 OpenAPI 책임별 tag와 문서 계약 검증 방법 추가
- README에 실행 방법 및 성능 측정 정리
- 대시보드에서 웹훅·아웃박스 지표 조회와 로컬 아웃박스 처리 확인 지원
- 프로젝트 가이드에서 결제 생명주기·승인 멱등성·웹훅·아웃박스 시나리오 실행 지원
- 아웃박스 재시도 정책을 환경변수로 설정할 수 있도록 추가
- 내부 운영 감사 이력 조회 endpoint와 traceId 연결 추가
- 대시보드 운영 탭에 웹훅·아웃박스 지표와 감사 이력 통합 표시
- H2 `CLOB`과 MySQL `TEXT` 차이를 database별 Flyway 위치로 분리
- 정산 원장 테이블을 H2 V2·MySQL V3 Flyway migration으로 분리
- `prod` 데이터소스의 필수 DB 접속 정보와 MySQL 드라이버·JPA 방언 기본값을 분리
- Docker Compose에 API service와 MySQL·Redis healthcheck 의존 순서 추가
- 승인 진행 중 Redis lease를 DB 행 잠금보다 먼저 확인하도록 승인 흐름 조정
- Health 응답에서 database·redis component 상태를 분리해 표시
- 운영 준비 단계의 측정값·제약·트러블슈팅을 README, ADR, 테스트 실행 기록에 추가

### Fixed
- 트랜잭션/import 정리
