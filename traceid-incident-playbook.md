# TraceId Incident Playbook (NEXT-13)

운영/로컬 환경에서 장애를 재현하고, 동일 traceId로 요청-응답-로그를 연결해 원인을 빠르게 좁히기 위한 체크리스트입니다.

## 1) 사전 준비
- 백엔드 실행: `./gradlew.bat bootRun`
- (선택) 대시보드 실행: `cd dashboard && npm run dev`
- 공통 헤더: `X-Trace-Id`

권장 traceId 규칙
- 형식: `incident-{yyyymmdd}-{short}`
- 예시: `incident-20260225-a01`

## 2) 장애 재현 체크리스트

### A. 정상 흐름 확인(기준선)
1. 고정 traceId로 결제 생성 요청
2. 같은 traceId로 단건 조회 요청
3. 응답 헤더 `X-Trace-Id`가 요청값과 일치하는지 확인

### B. 실패 흐름 재현
1. 존재하지 않는 `paymentId` 조회(404)
2. 비정상 상태 전이 호출(409)
3. 에러 바디의 `traceId`와 헤더 `X-Trace-Id` 일치 확인

### C. 로그 추적
1. `request.start` 로그 확인
2. `request.end` 로그 확인
3. 동일 traceId 기준으로 시작/종료 1쌍 매칭 확인

## 3) API 재현 템플릿 (PowerShell)

### 3-1. 결제 생성
```powershell
$traceId = "incident-20260225-a01"
$body = @{
  orderId = 900001
  idempotencyKey = "incident-key-900001"
  amount = 12345.67
  method = "CARD"
} | ConvertTo-Json

Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8080/api/v1/payments" `
  -Headers @{ "X-Trace-Id" = $traceId } `
  -ContentType "application/json" `
  -Body $body
```

### 3-2. 존재하지 않는 결제 조회(404 재현)
```powershell
$traceId = "incident-20260225-a02"
try {
  Invoke-RestMethod -Method Get `
    -Uri "http://localhost:8080/api/v1/payments/99999999" `
    -Headers @{ "X-Trace-Id" = $traceId }
} catch {
  $_.Exception.Response
}
```

### 3-3. 비정상 상태 전이(409 재현)
```powershell
$traceId = "incident-20260225-a03"
Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8080/api/v1/payments/1/cancel" `
  -Headers @{ "X-Trace-Id" = $traceId }
```

## 4) 로그 조회 템플릿

### 4-1. 콘솔 로그를 파일로 저장해 추적
```powershell
./gradlew.bat bootRun *> .\logs\app.log
```

### 4-2. 특정 traceId 검색
```powershell
$traceId = "incident-20260225-a01"
Select-String -Path .\logs\app.log -Pattern $traceId
```

### 4-3. 요청 시작/종료 로그만 필터링
```powershell
Select-String -Path .\logs\app.log -Pattern "request.start|request.end"
```

## 5) MySQL 점검 쿼리 템플릿

### 5-1. 최근 결제 20건
```sql
SELECT id, order_id, idempotency_key, amount, method, status, created_at
FROM payments
ORDER BY id DESC
LIMIT 20;
```

### 5-2. 특정 idempotency_key 조회
```sql
SELECT id, order_id, idempotency_key, status, created_at
FROM payments
WHERE idempotency_key = 'incident-key-900001';
```

### 5-3. 상태별 건수 확인
```sql
SELECT status, COUNT(*) AS cnt
FROM payments
GROUP BY status
ORDER BY status;
```

## 6) 장애 분석 기록 템플릿
- 장애 시각:
- traceId:
- 재현 API:
- 응답 코드/메시지:
- request.start 로그 시각:
- request.end 로그 시각:
- 처리시간(ms):
- 원인 가설:
- 조치 내용:
- 재발 방지 액션:

## 7) 완료 기준
- traceId 기준으로 정상/실패 흐름 각각 1회 이상 재현
- 로그에서 시작/종료 매칭 확인
- DB 쿼리 2개 이상으로 데이터 상태 점검
- 결과를 `test-execution-log.md`에 간단 기록
