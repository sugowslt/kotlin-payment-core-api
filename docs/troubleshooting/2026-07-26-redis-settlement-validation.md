# Redis 승인 멱등성·정산 원장 검증 중 발생한 오류

## 범위

2026-07-26 NEXT-31~33 구현 중 발생한 실제 컴파일·Spring 컨텍스트 오류와 해결 과정을 기록한다.

## 오류 1: 중복 no-op ledger 선언

- 증상: Kotlin 컴파일러가 `PaymentOutboxService.kt`의 `NoopSettlementLedgerService`에 `Redeclaration`을 보고했다.
- 원인: 인터페이스 도입 과정에서 기존 임시 no-op 선언을 제거하지 않고 새 선언을 추가했다.
- 대안: 이름을 바꿔 두 구현을 유지하는 방법도 있었지만, 같은 역할의 구현이 하나만 필요하므로 중복 선언을 제거했다.
- 결과: 컴파일이 통과했다.

## 오류 2: 계산기 인자명 불일치

- 증상: `SettlementLedgerService`에서 `grossAmount` named argument를 찾지 못했다.
- 원인: 기존 `SettlementAmountCalculator.calculate`의 인자명은 `amount`였고, 반환 모델의 필드명인 `grossAmount`와 혼동했다.
- 대안: 계산기 API의 인자명을 변경하는 방법도 있었지만 기존 NEXT-30 테스트와 호출 규격을 불필요하게 흔들 수 있어 호출부만 실제 시그니처에 맞췄다.
- 결과: 컴파일이 통과했다.

## 오류 3: Spring 컨텍스트의 계산기 빈 누락

- 증상: H2 전체 테스트에서 64개 중 33개가 `NoSuchBeanDefinitionException`으로 컨텍스트 초기화에 실패했다. 누락된 타입은 `SettlementAmountCalculator`였다.
- 원인: NEXT-30 계산기는 순수 Kotlin 클래스였고, NEXT-32 ledger service가 constructor injection으로 이를 요구하면서 Spring bean 등록이 필요해졌다.
- 대안: `@Bean` 설정 클래스를 추가하거나 ledger service 내부에서 직접 생성할 수 있었지만, 계산기를 애플리케이션과 테스트에서 동일하게 주입하려는 구조가 더 명확해 `@Component`를 적용했다.
- 결과: 수정 후 H2 전체 테스트가 성공했다.

## 제약

Redis 실제 연결과 MySQL V3 migration은 Docker Desktop 실행이 필요한 별도 검증이다. Docker를 임의로 실행하거나 외부 broker·외부 결제 API를 호출하지 않았다.
