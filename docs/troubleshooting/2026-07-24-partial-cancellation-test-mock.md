# 부분 취소 이력 테스트 mock 오류 기록

## 증상

취소 이력 repository를 추가한 뒤 H2 테스트에서 정상 취소와 동일 key 재요청 테스트가 실패했습니다.

```text
PaymentServiceTest: 50 tests completed, 4 failed
InvalidPaymentCancellationException: cancellation idempotency key is already used
ClassCastException: java.lang.Object cannot be cast to PaymentCancellation
```

## 원인 확인

단위 테스트의 `PaymentCancellationRepository`를 relaxed mock으로 추가했지만, `findByCancellationIdempotencyKey()`의 “이력 없음” 반환값을 지정하지 않았습니다. MockK가 null 대신 임의 객체를 반환해 정상 취소를 이미 사용된 key로 판단했습니다. `save()` 역시 반환값이 필요한 repository 메서드인데 기본 mock 반환값이 엔티티 타입과 맞지 않았습니다.

## 조치

테스트 setup에 다음 기본 동작을 명시했습니다.

- 취소 key 조회는 기본적으로 `null` 반환
- 취소 이력 저장은 전달받은 첫 번째 엔티티를 그대로 반환

추가로 기존 데이터 호환성을 위해 Payment에 저장된 마지막 취소 key가 같은 경우에도 기존 취소 응답을 재사용하도록 보완했습니다.

## 결과

수정 후 H2/MySQL 전체 테스트가 각각 54건, 실패 0건으로 통과했습니다.

## 선택 이유

운영 코드에 테스트 mock의 문제를 우회하는 조건을 추가하지 않고, repository의 실제 계약에 맞춰 테스트 fixture의 기본 반환값을 명시하는 방법을 선택했습니다. 이를 통해 이력 없음과 저장 성공이라는 repository 경계를 테스트에서 분명하게 표현할 수 있습니다.
