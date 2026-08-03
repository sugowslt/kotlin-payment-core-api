# Flyway 기반 schema migration

## Context

기존 기본 설정은 Hibernate `create-drop`에 의존했습니다. 로컬 테스트에는 편하지만, 운영 환경에서 애플리케이션 재기동이나 엔티티 변경이 schema를 임의로 바꾸게 만들 수 있습니다. `prod` 프로파일은 이미 `validate`를 사용하고 있었지만, 실제 schema를 생성·변경하는 버전 관리 수단이 없었습니다.

## Decision

- Flyway를 애플리케이션 시작 시 실행하도록 추가합니다.
- H2와 MySQL 모두에서 사용할 수 있도록 V1 초기 schema를 database별 migration 위치로 관리합니다. `CLOB` 매핑이 필요한 H2와 `TEXT` 타입을 사용하는 MySQL의 차이를 파일 단위로 분리합니다.
- MySQL의 `LocalDateTime` 정밀도는 V2 migration에서 `TIMESTAMP(6)`으로 보정합니다. 초 단위 `TIMESTAMP`만 사용하면 빠르게 생성된 결제의 정렬 순서가 보장되지 않습니다.
- 기본 JPA schema 전략은 `validate`로 고정하고, schema 변경은 `db/migration` 파일로만 수행합니다.
- 운영 환경에서는 Flyway history table과 migration 파일 버전을 함께 배포해 애플리케이션과 schema 버전을 맞춥니다.
- Flyway MySQL 지원 모듈은 별도 의존성으로 추가하고, 실제 외부 서비스나 유료 기능은 사용하지 않습니다.

## Alternatives

1. Hibernate `update`
   - 초기에는 편하지만 운영 schema 변경이 암묵적으로 발생하고 rollback 기준이 약해 선택하지 않았습니다.
2. 수동 SQL 실행
   - 단순하지만 실행 순서·중복 실행·현재 버전 확인을 별도로 관리해야 하므로 선택하지 않았습니다.
3. Liquibase
   - 변경 이력 관리가 가능하지만 현재 프로젝트는 SQL 중심의 간단한 migration 흐름이 적합해 Flyway를 선택했습니다.

## Consequences

- 애플리케이션 시작 전에 schema migration이 적용되어 H2/MySQL 환경 차이를 테스트할 수 있습니다. 기본 프로파일은 H2 migration을 사용하고, `prod` 프로파일은 MySQL migration을 사용합니다.
- 엔티티 변경 시 migration 파일과 검증 테스트를 함께 추가해야 합니다.
- 기존 운영 DB를 도입할 때는 이미 생성된 schema의 baseline 전략을 별도로 정해야 합니다.
