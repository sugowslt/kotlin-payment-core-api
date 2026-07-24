# Docker Desktop `dockerInference` 소켓 초기화 오류

## 증상

Docker Desktop이 시작되지 않고 다음 오류를 표시했습니다.

`initializing Inference manager: listening on unix://...\\dockerInference: remove ...\\dockerInference: The file cannot be accessed by the system`

이 상태에서는 Docker 엔진이 준비되지 않아 `docker ps`와 `docker version`이 응답하지 않았습니다.

## 확인 과정

1. Docker Desktop 실행 후 `docker ps`를 실행했지만 30초 후 타임아웃이 발생했습니다.
2. `C:\Users\user\AppData\Local\Docker\run\dockerInference`를 읽기 전용으로 확인했습니다.
3. 해당 항목은 0바이트 `ReparsePoint`였고, 마지막 수정일은 `2026-03-05`였습니다.
4. `com.docker.backend.exe.log`에서 같은 경로의 제거 실패와 backend 종료 로그를 확인했습니다.

## 원인

비정상 종료 이후 남은 `dockerInference` 재분석 지점이 Docker Desktop의 Inference manager 소켓 초기화를 방해한 것으로 판단했습니다. Docker 데이터 전체 손상이나 프로젝트 파일 문제로 확대할 근거는 확인하지 못했습니다.

## 해결 방법

사용자 승인을 받은 뒤 Docker 관련 프로세스를 종료하고, 확인된 stale 경로의 `dockerInference` 파일 하나만 제거했습니다. Docker Desktop을 다시 실행한 후 Docker 엔진 버전 `29.0.1`과 `docker ps` 응답을 확인했습니다.

## 대안과 선택 이유

- Docker Desktop 재시작만 시도: 먼저 시도할 수 있지만 같은 stale 항목이 남아 재발할 가능성이 있었습니다.
- Docker 데이터 정리 또는 공장 초기화: 해결 범위가 너무 크고 기존 이미지·컨테이너·설정을 잃을 수 있어 선택하지 않았습니다.
- 문제 파일만 제거 후 재시작: 로그와 파일 속성으로 확인된 정확한 대상만 변경하므로 데이터 손실 범위가 가장 작았습니다.

## 후속 검증

- 로컬 MySQL 8.4 컨테이너를 3307 포트로 실행했습니다.
- MySQL 동시 승인 통합 테스트를 실행해 상태 코드 `200`, `409` 조합을 확인했습니다.
- MySQL 기준 전체 테스트 28개가 성공했습니다.
