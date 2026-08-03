# ADR: prod 데이터소스 설정 하드닝

- 상태: Accepted
- 날짜: 2026-07-29
- 범위: Spring Boot `prod` 프로파일의 DB 연결 설정

## 배경

기본 설정은 H2 메모리 데이터베이스를 사용하고, `prod` 프로파일은 MySQL 연결을 사용합니다. 프로파일별 설정이 일부만 분리되어 있으면 MySQL JDBC URL에 H2 드라이버가 선택되는 식의 설정 불일치가 애플리케이션 시작 시점에 드러납니다.

## 결정

- `prod` 프로파일에서는 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`를 기본값 없이 요구합니다.
- `DB_DRIVER_CLASS` 기본값은 `com.mysql.cj.jdbc.Driver`로 둡니다.
- `JPA_DIALECT` 기본값은 `org.hibernate.dialect.MySQLDialect`로 둡니다.
- 다른 데이터베이스를 검증할 때는 해당 환경변수를 명시적으로 덮어씁니다.

## 선택 이유

- DB 접속 정보가 없는 운영 프로세스가 H2 기본값으로 조용히 시작하는 상황을 줄일 수 있습니다.
- MySQL URL·드라이버·방언의 기본 조합을 한 프로파일 안에서 일관되게 유지할 수 있습니다.
- 로컬 Compose와 CI가 같은 환경변수 계약을 사용하므로 재현 가능한 검증이 가능합니다.

## 검증과 제한

- Compose MySQL·Redis를 사용하는 `prod` 전체 테스트 67개가 성공했습니다.
- 기본 H2 전체 테스트 67개도 성공했습니다.
- 이 설정은 데이터베이스 연결 계약을 명확히 할 뿐이며, 실제 Toss API나 외부 운영 인프라 연결을 검증하지는 않습니다.
