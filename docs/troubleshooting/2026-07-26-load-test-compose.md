# 로컬 부하 테스트 실행 중 PowerShell 오류

## 증상

기본 H2 서버를 백그라운드로 실행한 뒤 부하 테스트를 수행하려고 `Start-Process`에 표준 출력과 표준 오류를 같은 로그 파일로 지정했습니다. PowerShell이 다음 이유로 프로세스 실행을 거부했습니다.

`This command cannot be run because "RedirectStandardOutput" and "RedirectStandardError" are same.`

## 원인

`Start-Process`는 stdout과 stderr의 redirect 대상에 동일한 경로를 허용하지 않습니다. 서버 자체 오류가 아니라 실행 래퍼의 로그 redirection 설정 오류였습니다.

## 검토한 대안과 선택

- 로그 redirection을 제거하고 콘솔에서 실행: 오류 로그와 기동 로그를 확인하기 어려워 선택하지 않았습니다.
- stdout·stderr를 동일 파일에 합치는 별도 shell piping: PowerShell·cmd 경계가 늘어나므로 선택하지 않았습니다.
- stdout과 stderr를 각각 별도 파일로 redirect: 실행 구조를 유지하면서 원인 확인이 가능해 이 방법을 선택했습니다.

## 결과

로그 파일을 분리한 뒤 기본 H2 서버가 정상 기동했고, 100건·동시성 10의 cursor 조회 측정을 완료했습니다. 100건 모두 HTTP 200, 실패율 0%, 평균 71.04ms, p50 14.26ms, p95 562.78ms였습니다. 측정 후 8080 포트는 비어 있는 상태를 확인했습니다.
