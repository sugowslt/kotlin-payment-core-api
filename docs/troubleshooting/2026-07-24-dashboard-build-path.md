# 대시보드 빌드 경로 오류 기록

## 증상

최종 검증 중 저장소 루트에서 `npm run build`를 실행했을 때 다음 오류가 발생했습니다.

```text
npm error Missing script: "build"
```

## 원인 확인

저장소 루트는 Kotlin/Spring Boot 프로젝트이고, 대시보드의 `package.json`은 `dashboard` 디렉터리에 있습니다. 따라서 루트의 npm 스크립트에는 `build` 명령이 존재하지 않습니다.

## 조치

대시보드 프로젝트 디렉터리에서 같은 명령을 다시 실행했습니다.

```powershell
Set-Location .\dashboard
npm run build
```

## 결과

Vite가 11개 모듈을 변환하고 `dist/` 산출물을 생성했으며, 빌드가 정상 종료되었습니다.

## 판단 근거

대시보드의 독립적인 프런트엔드 빌드이므로 저장소 루트에 npm 스크립트를 새로 추가하기보다, 기존 프로젝트 경계를 유지하고 실행 위치를 바로잡는 방법을 선택했습니다. 루트 `package.json`을 추가하거나 수정하면 현재 Kotlin 프로젝트의 구조와 사용자의 기존 대시보드 패키지 변경 범위를 불필요하게 넓히게 됩니다.
