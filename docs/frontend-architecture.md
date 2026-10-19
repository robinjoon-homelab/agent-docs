# 프런트엔드 아키텍처

웹 UI는 `frontend/`에 있다. 백엔드와 같은 헥사고날 구조를 따른다. 순수한 코어(도메인·애플리케이션)와 포트, 가장자리의 어댑터, 조립을 맡는 `config`로 나눈다. 백엔드 규칙은 [아키텍처와 도메인 경계](ddd-architecture.md), 테스트 기준의 원본은 [테스트 범위](development.md#테스트-범위)다.

## 실행 환경

- Node 24 LTS(`engines`)와 pnpm 12.14.0(`packageManager`)을 쓴다. 의존성 버전은 `package.json`에 정확히 고정하고 `pnpm-lock.yaml`을 커밋한다.
- 로컬 corepack이 pnpm 12를 실행하지 못하면 `npx pnpm@12.14.0 <명령>`을 쓴다.
- 타입 검사는 TypeScript 7(`tsgo` 별칭 패키지)로 한다. `typescript` 6.0은 typescript-eslint와 dependency-cruiser 전용이다.
- 개발 서버는 `/api`, `/oauth2`, `/login/oauth2`, `/logout`, `/oauth/token`, `/mcp`, `/.well-known`을 `http://localhost:8080`으로 프록시한다.
- 빌드 결과는 `frontend/dist/`다. `./gradlew build`가 Gradle이 내려받은 Node 24.21.0·pnpm 12.14.0으로 `pnpm check`(`frontendCheck` 태스크)를 실행하고 결과를 `application.jar`의 `static/`에 넣는다. 로컬에 Node 24가 없으면 `./gradlew frontendCheck`로 같은 검사를 실행한다.
- Spring Boot가 `/`에서 정적 파일로 제공하고, 화면 경로에는 `index.html`을 돌려준다. `assets/`의 해시 이름 파일은 1년 동안 캐시하고 나머지는 매번 다시 받는다. 배포 뒤 옛 `index.html`이 사라진 파일을 가리키지 않게 하기 위해서다.

## 패키지 배치

```text
src/domain/<area>/               순수 TypeScript: 타입, 불변식, 순수 함수
src/domain/shared/               영역 공용 값: Actor 표시, 상대 시간
src/application/<area>/          유스케이스(입력 포트)와 그 구현
src/application/<area>/ports/    출력 포트 인터페이스(Gateway)
src/application/shared/          공용 결과 타입, 공용 포트(ApiError, Clock)
src/adapters/inbound/ui/         React 화면·훅, Tiptap 편집기 통합
src/adapters/outbound/http/      fetch 기반 게이트웨이(JSON 변환, CSRF, 오류 변환)
src/adapters/outbound/memory/    로컬 미리보기용 메모리 게이트웨이
src/config/                      조립 루트와 서비스 Provider
src/main.tsx                     진입점
```

영역은 `session`, `space`, `document`, `thread`, `asset`, `search`, `inbox`, `activity`, `connection`, `metrics`다.

## 의존 규칙

`pnpm lint:arch`(dependency-cruiser, `.dependency-cruiser.cjs`)가 다음을 검사한다. 테스트 파일과 `src/test-support/`는 계층 규칙에서 제외하되 운영 코드가 이들을 참조하면 실패한다.

- `domain`은 `domain`만 import한다. npm 패키지, Node 내장 모듈, React, DOM, Tiptap, `window`·`localStorage`를 쓰지 않는다. 난수 같은 플랫폼 기능은 인자로 받는다.
- `application`은 `domain`과 `application`만 import한다. 한 영역은 다른 영역의 `application` 코드를 쓰지 않고 `application/shared`와 도메인 타입만 공유한다.
- `adapters/inbound/ui`는 `application`(유스케이스 타입·결과), `domain`(타입·순수 함수), `config/ServicesContext`만 쓴다. `adapters/outbound`를 직접 import하지 않는다.
- `adapters/outbound`는 `application/<area>/ports`와 `domain`만 쓴다. 유스케이스·UI·`config`를 참조하지 않는다. 한 영역의 어댑터는 그 영역의 포트와 `application/shared/ports`만 구현한다. `http`와 `memory` 그룹은 서로 참조하지 않는다.
- `config`와 `main.tsx`만 구현을 조립한다. 순환 의존과 해석할 수 없는 import는 금지한다.

UI는 `useServices()`로 받은 유스케이스만 호출한다. 게이트웨이는 UI에 노출하지 않는다.

## 조립

- `config/services.ts`의 `assembleServices`가 게이트웨이로 유스케이스를 만든다.
- 운영은 `config/httpServices.ts`(HTTP 게이트웨이)를 쓴다. `VITE_FAKE_API=true`(`pnpm dev:fake`)일 때만 `config/fakeServices.ts`를 동적으로 불러와 메모리 게이트웨이와 예제 데이터를 쓴다. 운영 빌드에는 메모리 어댑터가 들어가지 않는다.
- 브라우저 API(`fetch`, `document.cookie`)는 `config`에서 어댑터에 주입한다.
- HTTP 경로는 HTTP 출력 어댑터만 만든다. 예를 들어 첨부 주소 `/api/assets/{id}/content`는 `HttpAssetGateway`가 만들고, 편집기는 유스케이스의 `contentUrl`만 쓴다.

## 문서 모델과 편집기 계약

- 본문은 평평한 블록 목록이다. 목록 항목도 블록 하나이며 중첩은 `depth`(0~5)로만 표현한다.
- 블록 id는 서버 값을 그대로 보존한다. 블록 종류를 바꾸는 단축키·메뉴·Backspace는 id를 유지하고, 새 블록만 `[a-z0-9]` 10자 id를 받는다. 표의 행 id와 열 id 목록도 함께 유지한다.
- 인라인 텍스트는 GFM 인라인 마크다운이다. `domain/document/inline.ts`가 마크다운과 인라인 AST를 변환하고, 편집기 어댑터는 AST와 ProseMirror 노드·마크만 변환한다. 화면에 보이는 내용이 그대로인 블록은 저장된 마크다운을 바이트 단위로 다시 보낸다.
- 텍스트 앵커의 오프셋은 저장될 본문(앞뒤 공백을 다듬은 인라인 텍스트)의 `plainText`(마크업 제거, `<br>`은 `\n`)를 기준으로 한다. 앵커를 만들기 전에 대기 중인 저장을 먼저 보내고, 저장 결과가 성공일 때만 앵커를 만든다.
- 내용이 빈 문단·제목·인용·콜아웃은 저장하지 않는다. 붙여 넣은 블록·행·열이 문서에 이미 있는 id를 가지고 있으면 그 id를 지운다(잘라내기 뒤 붙여넣기는 원본이 먼저 사라지므로 id가 유지된다).
- 자동 저장은 입력이 멈추고 약 1초 뒤 전체 블록 목록을 마지막으로 받은 `baseRevision`과 함께 보낸다. 입력 즉시 세션을 '저장 대기'로 바꾸고, 저장되지 않은 내용(대기·저장 중·충돌·오류)이 있으면 페이지 이탈을 경고한다. 요청은 한 번에 하나씩 보내고 대기 중인 저장은 최신 내용 하나로 합친다.
- 저장·재저장은 `saved`·`conflict`·`error` 결과를 돌려준다. 저장을 전제로 하는 후속 동작(앵커 생성, 휴지통 이동, 성공 알림)은 `saved`일 때만 실행한다.
- 서버가 저장에 다른 작성자의 블록을 병합하면(`merged: true`) 응답 블록을 새 기준으로 삼고, 저장 요청 이후 사용자가 바꾸지 않은 블록만 편집기에 반영한다. 바꾼 블록은 다음 저장에서 새 기준 위로 보낸다. 409 충돌이면 입력을 유지하고 블록마다 내 버전·현재 버전을 고르게 한 뒤 병합 결과를 저장한다.
- 메타데이터(제목·경로·상태·태그·담당)는 먼저 화면에 반영하고, 서버가 거절하면 마지막 저장값으로 되돌린다.

## 테스트 범위

[백엔드 테스트 범위](development.md#테스트-범위)와 같은 기준을 쓴다. 도구는 vitest다.

- 본문에서 서로 다른 함수를 2개 이상 호출하는 함수만 테스트한다. 단순 위임은 테스트하지 않는다.
- 반환값, 던진 오류, 렌더링 결과, 결과 상태처럼 관찰 가능한 결과만 검증한다. `vi.fn`·spy·`toHaveBeenCalled`, 호출 기록·인자 캡처로 내부 호출을 검증하지 않는다.

| 대상 | 방식 |
| --- | --- |
| 도메인 | 순수 단위 테스트 |
| 유스케이스 | 데이터를 돌려주는 손으로 쓴 가짜 게이트웨이. 결과와 가짜 서버의 상태를 검증한다 |
| HTTP 어댑터 | `Response`를 돌려주는 가짜 `fetch`로 JSON 변환·오류 변환을 검증한다 |
| 편집기 어댑터 | 스키마·상태 수준 변환은 node 환경, 키 처리처럼 `Editor`가 필요한 것만 jsdom(`// @vitest-environment jsdom`) |
| 컴포넌트 | 컴포넌트 안에 로직이 있을 때만 |

종단 간(E2E) 테스트는 작성하지 않는다.

## 명령

`frontend/`에서 실행한다.

```sh
pnpm install
pnpm dev          # 백엔드(8080)에 프록시하는 개발 서버
pnpm dev:fake     # 백엔드 없이 메모리 예제 데이터로 미리보기
pnpm test         # vitest run
pnpm lint         # ESLint(typescript-eslint strict, react-hooks) + Prettier 검사
pnpm lint:arch    # dependency-cruiser 계층 규칙
pnpm build        # tsgo 타입 검사 + vite build → dist/
pnpm check        # lint, lint:arch, test, build 전체
```

변경을 마치면 `pnpm check`를 통과시킨다. 검사 실패를 숨기려고 규칙·테스트를 약화하지 않는다.
