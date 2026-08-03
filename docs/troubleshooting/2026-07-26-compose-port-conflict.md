# Docker Compose MySQL 포트 충돌

## 증상

`docker compose up -d` 실행 중 Redis는 시작됐지만 MySQL 컨테이너가 다음 오류로 시작되지 않았습니다.

`Bind for 0.0.0.0:3307 failed: port is already allocated`

## 원인

기존 사용자 컨테이너 `payment-core-mysql`이 MySQL 8.4를 호스트 3307에 이미 노출하고 있었습니다. 포트 점유 프로세스는 Docker Desktop의 `com.docker.backend`와 WSL relay로 확인됐고, 기존 컨테이너가 실제 점유 대상임을 `docker ps`로 확인했습니다.

## 대안과 선택

- 기존 `payment-core-mysql` 중지: 사용자 기존 리소스에 영향을 주므로 선택하지 않았습니다.
- 기존 컨테이너를 그대로 사용하고 Compose MySQL은 검증하지 않음: Compose의 MySQL 서비스 재현성을 확인할 수 없어 선택하지 않았습니다.
- Compose MySQL host port를 3308로 변경: 기존 컨테이너를 보존하면서 Compose의 MySQL 8.0을 별도 검증할 수 있어 선택했습니다.

## 결과

Compose MySQL은 `3308:3306`으로 healthy가 되었고 Redis도 `6379`에서 healthy가 되었습니다. MySQL Flyway V1·V2·V3 적용, Redis `SET NX EX` 중복 차단, Compose 환경 전체 테스트 67개 성공을 확인했습니다.
