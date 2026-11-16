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

영역은 `session`, `space`, `membership`, `document`, `thread`, `asset`, `search`, `inbox`, `activity`, `connection`, `navigation`, `statistics`, `portability`, `webhook`이다. 문서 변경 알림(`documentEvents`)은 `document` 영역 안의 유스케이스·포트다. 접근 권한 값(`Permission`)과 판정 함수는 `domain/space/permission.ts`에 있다.

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
- 브라우저 API(`fetch`, `document.cookie`, `EventSource`, `localStorage`, 브라우저 시간대)는 `config`에서 어댑터에 주입한다. `localStorage`를 쓸 수 없는 환경에서는 읽기를 빈 값, 쓰기를 버림으로 감싸 빠른 메모 화면이 깨지지 않게 한다.
- 멤버십은 `Services.memberships`(목록·초대·역할 변경·회수)로 쓰고 HTTP·메모리 조립 모두 `Gateways.memberships`를 넣는다. 메모리 회수도 행을 지우지 않고 회수 기록을 남긴다. 메모리 조립의 기본 세션은 사람 소유자이고 다른 사용자로 바꾸는 화면은 없다.
- 메모리 조립은 서버가 해야 하는 일을 성공으로 꾸미지 않는다. 가져오기·내보내기는 서버가 필요하다는 오류로 거절하고, webhook은 자기 상태만 바꾸며 아무 데도 보내지 않고, 통계는 측정하지 않은 값을 만들지 않는다. 메모리 저장소는 문서·스레드가 바뀔 때마다 문서별 변경 신호를 내고 `config`가 이를 문서 변경 알림에 연결한다.
- HTTP 경로는 HTTP 출력 어댑터만 만든다. 예를 들어 첨부 주소 `/api/assets/{id}/content`는 `HttpAssetGateway`가 만들고, 편집기는 유스케이스의 `contentUrl`만 쓴다. 문서 화면·댓글·이력은 문서 ID를 함께 넘겨 `/api/documents/{문서}/assets/{본문의 원래 ID}/content`·`source`를 쓴다. 다른 스페이스로 옮긴 문서의 첨부를 그 문서 기준으로 해석하기 위해서다. 본문·이력의 첨부 ID는 바꾸지 않는다. 정확한 요청·응답은 REST Docs가 원본이다.

## 문서 모델과 편집기 계약

- 본문은 평평한 블록 목록이다. 목록 항목도 블록 하나이며 중첩은 `depth`(0~5)로만 표현한다.
- 블록 id는 서버 값을 그대로 보존한다. 블록 종류를 바꾸는 단축키·메뉴·Backspace는 id를 유지하고, 새 블록만 `[a-z0-9]` 10자 id를 받는다. 표의 행 id와 열 id 목록도 함께 유지한다.
- 인라인 텍스트는 GFM 인라인 마크다운이다. `domain/document/inline.ts`가 마크다운과 인라인 AST를 변환하고, 편집기 어댑터는 AST와 ProseMirror 노드·마크만 변환한다. 화면에 보이는 내용이 그대로인 블록은 저장된 마크다운을 바이트 단위로 다시 보낸다.
- 텍스트 앵커의 오프셋은 저장될 본문(앞뒤 공백을 다듬은 인라인 텍스트)의 `plainText`(마크업 제거, `<br>`은 `\n`)를 기준으로 한다. 앵커를 만들기 전에 대기 중인 저장을 먼저 보내고, 저장 결과가 성공일 때만 앵커를 만든다.
- 내용이 빈 문단·제목·인용·콜아웃은 저장하지 않는다. 붙여 넣은 블록·행·열이 문서에 이미 있는 id를 가지고 있으면 그 id를 지운다(잘라내기 뒤 붙여넣기는 원본이 먼저 사라지므로 id가 유지된다).
- 자동 저장은 입력이 멈추고 약 1초 뒤 전체 블록 목록을 마지막으로 받은 `baseVersion`과 함께 보낸다. 문서를 떠나면 마지막으로 저장에 성공한 버전을 담아 확정 전 입력을 리비전으로 확정하도록 요청한다. 서버는 그 버전과 작성자가 확정 전 입력과 맞을 때만 확정한다. 입력 즉시 세션을 '저장 대기'로 바꾸고, 저장되지 않은 내용(대기·저장 중·충돌·오류)이 있으면 페이지 이탈을 경고한다. 요청은 한 번에 하나씩 보내고 대기 중인 저장은 최신 내용 하나로 합친다.
- 저장·재저장은 `saved`·`conflict`·`error` 결과를 돌려준다. 저장을 전제로 하는 후속 동작(앵커 생성, 휴지통 이동, 성공 알림)은 `saved`일 때만 실행한다.
- 기준 버전이 낡아 저장이 거절되면(409) 응답의 현재 블록·버전을 새 기준으로 삼는다. 내 기준 이후 한쪽만 바꾼 블록은 바꾼 쪽을 따르고, 그사이 다른 쪽이 더한 블록은 지금 앞 블록 뒤에 둔 채 새 버전으로 다시 보낸다. 같은 블록을 양쪽이 다르게 바꿨거나 한쪽이 지운 블록을 다른 쪽이 고쳤으면, 입력을 유지하고 블록마다 내 입력·현재 내용을 고르게 한 뒤 병합 결과를 저장한다. 다시 보낸 뒤 사용자가 바꾸지 않은 블록만 편집기에 반영한다.
- 메타데이터(제목·경로·상태·태그·담당)는 먼저 화면에 반영한다. 기준 버전이 낡아 거절되면 바꾸려는 필드를 다른 쪽이 바꾸지 않았을 때만 새 버전으로 다시 보내고, 그 밖의 거절은 마지막 저장값으로 되돌린다.
- 다른 창·에이전트의 변경 신호(SSE)를 받으면 저장·확정 없이 최신 문서를 읽어 기존 세션 순서(`chain`) 안에서 내 기준·내 입력·현재 내용을 3-way로 합친다. 같은 블록을 양쪽이 바꿨으면 위의 블록별 선택에 남긴다. 기존 충돌, 더 낡은 버전, 다른 문서의 응답은 덮어쓰지 않는다. 메타데이터 입력 중에도 신호가 입력과 원래 충돌 기준을 바꾸지 못한다. 댓글은 스레드 상태의 재조회 세대 보호를 따른다. 연속 신호는 하나로 합쳐 차례로 처리한다.
- 편집 시간 통계는 리비전 확정(30초·600초)과 따로 잰다. 화면은 전경 편집 구간마다 새 ID와 입력 기록기를 만들고, 본문·메타 필드별 실제 입력 시각을 그 저장 요청의 `inputTrace`로 보낸다. `inputTrace`는 요청 전체의 `batchId`와 전경 구간별 `traces[*]`(전경 ID `tabId`, 구간 묶음 ID, `intervals`)를 담는다. 숨겨지거나 떠나 닫힌 전경도 아직 보내지 않은 입력이 있으면 남겨 두었다가 다음 저장에 지금 전경과 함께 보낸다. 그 저장이 성공하면 모든 구간의 입력을 함께 채택하되, 각 입력은 원래 전경 구간에 남고 서로 다른 전경 사이의 배경 시간은 합치지 않는다. 한 전경의 `intervals`는 120초 이상 간격에서 나누고 시작·끝 두 시각으로 줄이지 않는다. 버전을 올린 성공 저장에만 채택하고, 바뀐 것 없는 저장은 시각을 버리며, 실패·409 재시도는 같은 기록을 유지한다. 화면이 숨겨지거나 떠나면(`hidden`·`pagehide`·unmount) 마지막 성공 시각으로 구간을 닫고, 다시 돌아온 입력은 새 구간이다. 비행 중인 요청은 시작할 때의 구간을 기억한다. 통계 실패는 저장 상태에 영향을 주지 않는다.
- GitHub 짧은 참조(`#123`, 커밋 SHA, 파일 경로)는 본문을 바꾸지 않는 표시용 decoration이다. 스페이스에 레포 링크가 하나면 그 레포, 여럿이면 고른 기본 레포가 있을 때만 링크하고, 파일은 `HEAD`로 연다. 코드·기존 링크·수식은 제외한다.
- 수식 `$...$`는 원문을 그대로 왕복하고 옆에 KaTeX 미리보기만 보여 주며, 독립 줄 `$$`는 `language=math`, `displayMath=true` 코드 블록이다. 기존 `math` 펜스는 `displayMath`를 넣지 않아 바이트·해시를 유지한다.
- HTML 시안은 `iframe srcdoc`에 `sandbox="allow-scripts"`만 주고 same-origin·top navigation·popup·form을 허용하지 않는다. 문서 앞의 CSP가 네트워크·폼·base·중첩 프레임을 막고 원문을 부모 DOM에 넣지 않는다. 시안 교체는 대기 입력을 저장·확정한 뒤 새 첨부를 올리고 같은 블록 ID의 첨부 ID만 바꿔 기존 저장 경로로 보낸다.
- 문서 이사는 대기 중인 메타·본문 저장이 성공한 뒤에만 목적 스페이스·경로를 고르게 하고, 보내기 직전에 다시 저장·확정을 확인한다. 성공하면 같은 문서 ID의 새 스페이스 주소로 다시 연다.
- 빠른 메모(`/m`)는 정확한 경로의 문서가 없으면 만들고, 있으면 마지막 블록 뒤에 새 블록을 넣는 기존 쓰기를 쓴다. 결과가 불분명할 때만 같은 `opId`·같은 본문으로 다시 보내고, 거절되면 입력을 지우지 않고 현재 내용을 다시 읽는다. 보내지 못한 입력은 화면 메모리에만 두며 오프라인 큐·서비스 워커는 없다. PWA는 manifest·아이콘으로 홈 화면 실행만 지원한다.

## 권한과 열린 문서 보존

- 화면이 무엇을 보여 줄지는 REST 응답 최상위 `permission`(`read`·`comment`·`edit`·`owner`)으로 정한다. 스페이스는 `Space.permission`, 문서는 그 문서가 지금 있는 스페이스 기준의 `AgentDocument.permission`이다. 멤버십 역할 `MembershipRole`(`editor`·`commenter`)과 접근 권한 `Permission`은 다른 타입이고 서로 바꿔 쓰지 않는다. 편집자 역할이 곧 `edit` 권한은 아니다. 최종 판정은 서버가 한다.
- 모르는 권한 값은 `read`로 읽는다. 필드가 아예 없을 때만 예전처럼 전역 사람 소유자(`me.owner`)를 `owner`로 보고, 그 밖에는 `read`다. 회수로 권한을 잃은 상태(`null`)는 이 소유자 대체보다 우선한다. 쓰기 요청에는 읽기 전용인 `permission`을 다시 담지 않는다.
- `edit` 이상: 문서 만들기, 본문·메타 편집, 이동·이사, 휴지통 이동과 꺼내기, 이력 복원·블록 복원·리비전 이름, 수정 제안 결정, 모든 스레드 해결. `comment` 이상: 댓글·답글·반응·수정 제안, 자기가(사람으로) 연 스레드 해결. 이력 화면은 지금 문서의 권한을 쓰고 과거 리비전이 있던 스페이스를 근거로 쓰지 않는다.
- 새 스페이스, 스페이스 설정·멤버, 연결 승인·관리, 데이터·내보내기, webhook의 메뉴와 경로는 전역 사람 소유자에게만 보인다. 경로 보호는 보호한 화면이 데이터를 읽기 전에 판단한다. 영구 삭제도 소유자에게만 보인다.
- 열린 문서가 권한 신호를 받으면 그 문서가 지금 있는 스페이스를 다시 읽고, 그 스페이스 응답의 권한만 `SpacePermissionsContext`로 사이드바와 공유한다. 강등되면 오래된 만들기·끌어 옮기기 컨트롤도 닫힌다. 다시 읽는 사이 문서가 다른 스페이스로 옮겨졌으면 공유하지 않으므로 예전 스페이스 권한이 새 스페이스 권한을 덮지 않는다. 문서 하나의 회수(`not_found`)는 그 문서만 닫고 스페이스 권한을 바꾸지 않는다.
- 문서 쓰기는 `DocumentEditorSession.setWritable`이 연다. 닫혀 있으면 로컬 입력은 계속 쌓이고 서버에는 아무것도 보내지 않는다. 대기 중인 저장·409 재시도·메타 저장과 그 409 재시도·이탈 확정은 실제로 보내기 직전에 게이트를 다시 확인한다. 충돌 해결은 시작할 때와 조회 결과를 적용하기 전에 다시 확인하고, 닫혔으면 기준과 충돌 목록을 바꾸지 않는다. 닫힌 뒤 끝난 응답이 실패해도 초안을 마지막 저장값으로 되돌리지 않고(메타 409면 기준도 바꾸지 않는다), 성공해도 기준 버전과 본문만 맞추며 그 뒤 입력을 덮지 않는다. `setWritable(true)`는 건너뛴 저장을 자동으로 다시 보내지 않고, 다음 입력·저장·확정이 평소처럼 보낸다.
- 문서 변경 알림의 `permission` 신호를 받으면 바로 쓰기를 닫는다. `edit` 이상으로 돌아와도 최신 문서를 다시 읽고 `refreshRemote()`로 새 기준과 충돌을 정리한 뒤에만 다시 연다. 충돌이 남으면 닫힌 채 해결 버튼만 연다. `revoked` 신호를 받으면 같은 순간에 `EventSource`를 닫아 재연결을 멈추고, 그 뒤의 신호는 무시한다. 늦게 도착한 예전 응답이 회수나 새 문서 상태를 덮지 않게 세대를 확인한다.
- 강등·회수, 열린 문서를 다시 읽다 받은 401·404 때문에 편집기(`DocumentEditor`)나 열린 손그림 창을 언마운트하지 않는다. 읽기 전용 배너와 입력 복사 버튼(제목·태그·경로 같은 메타와 전체 블록 JSON)을 남긴다. 이탈 확정과 언마운트 확정은 그때의 최신 권한과 `isWritable()`을 다시 본다.
- 성공적으로 연 문서 안에서만 `RetainedDocumentContext` 보호 경계를 둔다. 그 안의 조회(`useLoad`)가 받은 401은 로그인 화면으로 넘기지 않고 오류 상태로 남긴 채 문서 회수(`unauthenticated`)로 알린다. 회수 뒤에는 그 경계 안의 새 조회를 시작하지 않는다. 마지막 멤버십이 회수되어 로그인 허용이 없어진 사람은 새 조회에서 401과 로그인 안내를 받고, 이미 열린 초안은 화면에 남는다. 경계 밖 화면과 문서를 처음 열 때의 401은 지금처럼 로그인 화면으로, 404는 없는 문서 화면으로 간다. 새 문서 조회가 실패했을 때 예전 문서를 대신 보여 주지 않는다.

## 손그림

- 손그림은 `DrawingBlock`(`svgAssetId`, `sceneAssetId`, `alt`)이다. 편집기 스키마의 `drawing` 노드가 두 첨부 ID와 대체 텍스트를 들고 HTML 직렬화·복사·붙여넣기에서도 함께 옮기며, 붙여 넣은 복사본은 새 블록 ID만 받는다. 저장 확인 전에는 이 탭이 올린 SVG를 보여 주고, 확인 뒤에는 문서 범위 첨부 경로로 정적 SVG를 그린다. 이력 화면도 정적 SVG다.
- 편집은 모달 창에서 `@excalidraw/excalidraw` 0.18.1로 한다. 화면 폭이 1023px 이하이면 그림은 읽기만 하고 편집 대신 안내를 보인다. 첨부 한도는 20MB이고 그림 안 이미지 하나의 4MB 한도는 Excalidraw 기본값이다.
- 저장을 누르면 같은 순간의 장면으로 장면 JSON과 SVG를 만들고, 그 순간까지의 손그림 입력을 함께 고정한다. 대기 중인 본문을 먼저 저장·확정하고, 장면, SVG 순으로 올린 뒤 같은 블록 ID에 새 쌍을 적용해 기존 본문 저장으로 보낸다(`editor/drawing/drawingSave.ts`). 본문 저장·확정 뒤, 장면 업로드 뒤, SVG 업로드 뒤, 적용 전에 쓰기 게이트와 편집 가능 여부를 다시 확인하고, 닫혔으면 다음 업로드·쓰기를 시작하지 않는다. 이미 보낸 요청은 되돌리지 않으므로 올라간 첨부는 남고, 블록은 이전 쌍을, 창은 장면을 그대로 둔다. SVG에 바깥 주소(폰트를 불러오지 못한 경우 포함)가 남아 있으면 저장하지 않고 안내한다.
- 손그림 편집 시간은 기존 편집 시간 통계에 그 변경 시각으로 기록한다. 1초 샘플링은 하지 않는다. 장면 내용 지문이 바뀐 실제 변경만 입력이고, 보기 이동·확대·선택·도구 변경과 처음 열 때의 정규화는 입력이 아니다. `DrawingInputRecorder`(`mark`·`reserve`·`discard`)는 창마다 입력을 본문과 따로 모은다. 저장 순간에 고정한 `InputReservation`은 적용 저장(`saveContent`의 `drawingInput: { reservation, placement }`)에서 그 블록 ID와 SVG·장면 쌍에 묶인다. 그 뒤로는 재시도든 다른 본문 저장이든 보내는 본문이 그 블록과 쌍을 그대로 담을 때만 같이 간다. 실린 예약은 버전을 올린 성공 저장에서 채택하고, 바뀐 것 없는 저장은 버리며, 409·오류 동안은 유지한다. 성공이나 바뀐 것 없는 응답 때 현재 본문에도 그 블록과 쌍이 없으면(그림 삭제, 충돌에서 상대 쪽 선택) 그 예약의 고정 입력만 버리고, 뒤 입력과 다른 그림의 예약은 남긴다. 이미 그 쌍을 담아 보낸 저장의 성공은 나중에 화면에서 지워도 채택된다. 블록에 적용하기 전에 실패하면 창과 입력을 그대로 두어 다시 저장할 수 있고, 적용 없이 창을 닫거나 취소할 때 버린다. 숨겨진 전경의 입력은 원래 전경 구간에 남는다. 문서 화면의 `drawingInput={inputTime.drawing}` 연결은 바뀌지 않았고, 서버 통계·새 BC·완료 버튼도 더하지 않았다.
- 외부로 나가는 경로를 막는다. 파일 끌어놓기, 주소로 된 이미지가 든 HTML 붙여넣기(브라우저 파서로 `img`의 `src`를 읽어 문자 참조로 숨긴 주소도 막는다), 라이브러리 게시, AI 프레임(`aiEnabled={false}`)과 웹 임베드(`validateEmbeddable={false}`)를 쓰지 않는다. 폰트는 앱이 함께 내보내는 로컬 파일만 쓰고 CDN(esm.sh)으로 가지 않는다. 장면의 이미지는 `data:` 주소만 받는다.
- 폰트와 라이선스는 버전 폴더 `/assets/excalidraw/0.18.1/`(`config/excalidrawAssets.ts`의 `EXCALIDRAW_ASSET_BASE`)에 폰트 7가족, `licenses/` 원문 9개(폰트 8가족과 Excalidraw), `THIRD-PARTY-NOTICES.txt`를 함께 배포한다. Assistant는 Excalidraw CSS가 참조해 CSS 번들과 함께 `assets/` 아래로 나간다. 원문 라이선스를 확인하지 못한 Liberation Sans 1.05(font family 9)는 배포하지 않으므로, 그 글꼴을 쓴 장면은 저장할 수 없다. `vite.config.ts`는 설치 버전이 `EXCALIDRAW_VERSION`과 다르거나 분류하지 않은 폰트 가족이 생기면 빌드를 실패시킨다. 개발 서버는 `node_modules` 안의 패키지 폰트를 쓴다.
- 운영 빌드와 개발 사전 번들링은 고정한 Excalidraw 패키지의 CDN 폰트 주소 추가 문장을 제거한다. 검토한 문장이 정확히 한 번 나타나지 않거나 변환이 누락되면 실패하며, 패키지를 올릴 때 폰트와 두 빌드의 변환을 함께 검토한다. 원래 패키지와 폰트 파일은 고치지 않고 수정 사실을 함께 배포하는 고지에 남긴다.
- 운영 빌드에서 글꼴 부분 추출 worker가 편집기와 공유하는 두 보조 모듈을 별도 청크로 묶는다. worker가 정적으로 불러오는 청크에 원래 의존하지 않는 모듈이 섞이면 빌드를 실패시킨다. 번들러가 삽입하는 정확한 런타임 보조 모듈만 예외이며, 앱 진입 코드나 화면 코드를 worker에 포함하지 않는다.
- 패키지는 `0.18.1`로 정확히 고정한다. Excalidraw는 React 19를 피어로 받지만, 전이 의존 `@radix-ui/react-tabs@1.0.2` 묶음은 React 18까지만 선언해 설치 때 피어 경고가 난다. 이 경고는 숨기거나 `overrides`로 덮지 않고 실행 검증 대상으로 남긴다.
- 페이지 CSP는 서버가 `index.html`에 `font-src 'self' data:; connect-src 'self'`로 붙이고, 개발 서버도 같은 정책에 HMR용 `ws://`와 메모리 조립의 `blob:`만 더한다. 첨부 응답은 `font-src data:`만 더해 SVG에 넣은 `data:` 폰트만 읽힌다.

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
