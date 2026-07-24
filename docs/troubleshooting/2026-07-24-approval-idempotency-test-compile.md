# 승인 멱등키 작업 중 테스트 컴파일 오류

- 발생일: 2026-07-24
- 범위: 승인 멱등키 구현 검증
- 상태: 해결

## 증상

`gradlew.bat test --no-daemon` 실행 중 `compileTestKotlin` 단계에서 테스트 컴파일이 실패했습니다.

주요 오류는 다음과 같습니다.

```text
Unresolved reference: existsByIdempotencyKey
The boolean literal does not conform to the expected type Unit
```

## 원인 분석

`PaymentRepository`의 실제 메서드는 soft-delete 조건을 포함한 다음 이름으로 변경되어 있었습니다.

```kotlin
existsByIdempotencyKeyAndDeletedFalse(idempotencyKey: String): Boolean
```

그러나 `PaymentServiceTest`는 이전 메서드명인 `existsByIdempotencyKey`를 MockK로 설정하고 있었습니다. 따라서 테스트 소스가 현재 repository 인터페이스와 동기화되지 않은 상태였습니다.

## 검토한 해결 방법

1. 운영 코드에 이전 메서드명을 다시 추가합니다.
2. repository 인터페이스에 호환용 메서드를 추가합니다.
3. 테스트 mock을 현재 운영 코드의 메서드명으로 수정합니다.

## 선택한 해결 방법

3번을 선택했습니다. 운영 코드의 soft-delete 조회 규칙을 약화시키지 않고, 테스트가 실제 계약을 검증하도록 만드는 방법이기 때문입니다.

추가로 결제 조회 mock도 같은 기준으로 확인하여 `findById`를 `findByIdAndDeletedFalse`로 맞췄습니다.

## 검증

```text
gradlew.bat test --no-daemon
BUILD SUCCESSFUL
25 tests
failures: 0
errors: 0
skipped: 0
```

## 재발 방지

- repository 메서드명을 변경할 때 모든 mock·stub 호출을 함께 검색합니다.
- 컴파일 단계가 포함된 전체 테스트 명령을 기능 변경 후 실행합니다.
- soft-delete 조건을 포함하는 repository 메서드는 테스트에서도 동일한 조건을 사용합니다.
