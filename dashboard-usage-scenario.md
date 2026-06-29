# Dashboard Usage Scenario (NEXT-10)

Vue 대시보드 MVP를 이용해 백엔드 구현 결과를 시각적으로 확인하는 절차입니다.

## 1) 실행 준비
1. 백엔드 실행
   - `./gradlew bootRun` (Windows: `./gradlew.bat bootRun`)
2. 대시보드 실행
   - `cd dashboard`
   - `npm install`
   - `npm run dev`
3. 접속
   - `http://localhost:5173`

## 2) 시나리오 A: 결제 목록 조회(커서)
목표
- `GET /api/v1/payments/cursor` 응답이 대시보드 표에 반영되는지 확인

절차
1. 대시보드 첫 진입 시 목록이 자동 로드되는지 확인
2. 상단 `새로고침` 클릭 시 목록/응답시간(ms)이 갱신되는지 확인
3. 하단 `더 보기` 클릭 시 다음 커서 페이지가 이어서 붙는지 확인
4. `hasNext` 값이 false가 되면 더 이상 로드되지 않는지 확인

검증 포인트
- ID가 내림차순으로 표시됨
- 중복 행 없이 이어붙기 동작
- `최근 조회 응답시간` 표시

## 3) 시나리오 B: 상태 전이(승인/취소)
목표
- `POST /api/v1/payments/{id}/approve|cancel` 결과가 화면에서 확인되는지 검증

절차
1. 목록에서 결제 ID 하나 선택
2. `paymentId` 입력란에 ID 입력
3. `APPROVE` 클릭 후 성공 메시지 및 목록 상태 갱신 확인
4. 같은 ID에 `CANCEL` 클릭 후 성공 메시지 및 목록 상태 갱신 확인
5. 비정상 전이(예: CANCELED 상태에서 APPROVE) 시 실패 메시지 노출 확인

검증 포인트
- 성공: `성공: status=...`
- 실패: `실패: ...`
- 동작 후 목록 자동 새로고침

## 4) 시나리오 C: 지표 요약 카드
목표
- 현재 로드된 목록 기준 상태 집계(PENDING/APPROVED/CANCELED) 확인

절차
1. 최초 로드 직후 카드 값 기록
2. 상태 전이 실행 후 카드 값 변화 확인
3. `더 보기`로 데이터가 늘어날 때 카드가 재계산되는지 확인

검증 포인트
- 카드 3종 수치가 표 데이터와 일치

## 5) 스크린샷 체크리스트 (선택)
아래 3장은 선택사항이며, 필요 시 `project1/docs/screenshots/`에 저장
- `dashboard-list-cursor.png` : 목록 + hasNext + 응답시간
- `dashboard-transition-result.png` : 상태 전이 성공/실패 메시지
- `dashboard-metrics-summary.png` : 지표 카드 상태

## 6) 기록 규칙
- 시나리오 수행 날짜/환경(H2 또는 MySQL)을 README에 함께 기록
- 이슈 문서(`week1-issues.md`)에 완료 상태 및 결과 요약 반영
- 스크린샷 없이도 실행 로그 + 시나리오 체크 결과로 완료 처리 가능

## 7) 시나리오 D: traceId 기반 운영 점검
목표
- 대시보드에서 요청 단위 traceId를 확인해 백엔드 로그와 추적 가능한지 검증

절차
1. 목록 `새로고침` 실행 후 `LAST_TRACE_ID` 값 확인
2. 상태 전이 성공 1회 수행 후 메시지에 포함된 traceId 확인
3. 실패 케이스(존재하지 않는 paymentId 등) 1회 수행 후 실패 메시지 traceId 확인
4. `TOTAL/SUCCESS/FAILED` 카운트 증가가 실행 결과와 맞는지 확인

검증 포인트
- 응답 헤더 `X-Trace-Id`가 대시보드에 표시됨
- 성공/실패 모두 traceId 추적 가능
- 운영 지표(TOTAL/SUCCESS/FAILED)가 요청 결과와 일치
