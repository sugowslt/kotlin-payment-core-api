# Toss gateway adapter 테스트 트러블슈팅

## 작업 중 발생한 오류

Toss 승인 adapter와 로컬 mock 서버 테스트를 추가하는 과정에서 세 가지 실제 오류가 발생했습니다.

1. `RestClient.ResponseSpec` 컴파일 오류
   - `.body<TossConfirmResponse>()`를 현재 Spring 버전의 Kotlin API가 해석하지 못했습니다.
   - `.body(TossConfirmResponse::class.java)`로 변경했습니다.

2. mock 서버 대신 `localhost` 연결 시도
   - adapter가 생성될 때 타임아웃용 `SimpleClientHttpRequestFactory`를 항상 주입해 `MockRestServiceServer`가 설정한 factory를 덮어쓰고 있었습니다.
   - 운영용 `RestClient` 생성은 configuration으로 이동하고, gateway는 완성된 `RestClient`를 주입받도록 분리했습니다.

3. mock 요청 URI와 인증 헤더 불일치
   - 테스트 builder에 base URL을 설정하지 않아 `/v1/payments/confirm`과 전체 URL 기대값이 달랐습니다.
   - builder에 `http://localhost` base URL을 설정했습니다.
   - `setBasicAuth(secretKey)`가 기대한 `secretKey:` 인코딩과 달라 `setBasicAuth(secretKey, "")`로 수정했습니다.

## 선택 이유

- 컴파일 오류는 현재 Spring API 시그니처에 맞추는 것이 가장 작은 수정이었습니다.
- RestClient 생성과 gateway 로직을 분리하면 운영 타임아웃 설정과 테스트용 request factory가 서로 침범하지 않습니다.
- Basic 인증은 Toss 공식 문서의 secret key 뒤 콜론을 포함한 Base64 형식을 그대로 테스트하도록 했습니다.

## 검증 결과

- Toss adapter mock 테스트 3개 성공
- Basic 인증, `Idempotency-Key`, 승인 요청 본문 검증
- 5xx 응답 1회 재시도 검증
- `Payment-Key` 누락 시 외부 요청 전송 전 400 분기 검증
