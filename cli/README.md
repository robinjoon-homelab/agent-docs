# adocs CLI

JDK 25가 설치된 macOS/Linux에서 저장소 루트의 Gradle wrapper로 설치한다. 서버 설치·배포와 CLI 설치는 별개이며 release workflow는 없다.

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 25) # macOS
./gradlew :cli:installDist
export PATH="$PWD/cli/build/install/adocs/bin:$PATH"
adocs help
```

Linux에서는 `JAVA_HOME`에 JDK 25 경로를 지정한다. PATH에는 `bin`만 지정하고 배포 디렉터리의 `lib`를 함께 보존한다. 다른 위치에 설치하려면 `cli/build/install/adocs/` 전체를 복사하고 그 `bin`을 PATH에 추가한다. 업데이트는 원하는 커밋을 받은 뒤 같은 `:cli:installDist`를 실행하고 복사 설치라면 배포 디렉터리 전체를 다시 복사한다. JVM과 의존 라이브러리가 함께 필요하므로 `bin/adocs`만 복사하지 않는다.

```sh
export ADOCS_HOST=https://docs.example.com
adocs login
adocs spaces --json
adocs list --space my-project
adocs list # 현재 레포 git origin으로 스페이스 찾기
adocs read DOCUMENT_UUID
adocs read DOCUMENT_UUID --flow-text 저장 --flow-kind button # 화면 흐름 문서의 대상 찾기(--flow-screen, --flow-limit, --flow-offset)
adocs flow-read DOCUMENT_UUID --body find.json --json # {"view":"blocks","kind":"screen"}
adocs flow-author DOCUMENT_UUID --body markup.json --json
adocs flow-patch DOCUMENT_UUID --body change.json --json
adocs flow-render DOCUMENT_UUID --body selection.json --json
adocs flow-upgrade DOCUMENT_UUID --body upgrade.json --json
adocs search --query 'q=search&space=my-project' --json
adocs create --space my-project --body create.json --json
adocs edit DOCUMENT_UUID --body edits.json --json
adocs pull DOCUMENT_UUID document.md
# document.md의 본문을 편집한다.
adocs push document.md --json
adocs upload image.png --space my-project --opId logo-20261119
adocs import-folder ./notes --tool obsidian --space my-project --json
adocs logout
```

`--host https://HOST`는 `ADOCS_HOST`보다 우선한다. HTTP·userinfo·경로·query·fragment가 있는 호스트는 거부한다. 로그인은 공개 클라이언트 `https://HOST/oauth/clients/adocs`, PKCE S256, state와 issuer 검증, `https://HOST/mcp` resource, `127.0.0.1` 임의 포트 콜백을 사용한다. 기존 승인 화면에서 범위와 스페이스를 선택한다. 브라우저를 열지 못하면 stderr에 승인 주소를 표시하므로 직접 연다. 이 주소는 공유하거나 로그로 수집하지 않는다. 승인 대기는 180초다.

자격 파일은 `${XDG_CONFIG_HOME:-$HOME/.config}/adocs/<origin SHA-256>/credentials.json`에 0600으로 저장한다. 디렉터리는 0700이며 파일을 원자 교체한다. refresh는 호스트별 파일 잠금 안에서 최신 파일을 다시 읽고 수행한다. 로컬 logout은 자격 파일만 제거한다. 서버의 연결 회수는 연결 설정에서 수행한다. OAuth 대신 기존 스크립트 토큰을 `ADOCS_TOKEN`으로 제공할 수 있으며 환경값은 파일에 저장하지 않는다. 토큰을 명령행 인자에 넣지 않는다.

`--json` 성공 출력은 stdout의 JSON 하나이고 실패는 stderr의 `{code,fix,status,retryAfter}` JSON 하나다. 서버가 충돌 근거로 보낸 `conflicts`, `currentVersion`, `currentDocument`는 JSON과 일반 오류 출력에 함께 표시한다. 로그인 주소 안내는 stderr만 사용한다. 종료코드는 성공 0, HTTP 409 충돌 3, 그 외 오류 1이다. 429의 `Retry-After`를 전달하고 자동 재시도하지 않는다. `legacy_operation`과 `result_removed`도 새 opId를 만들어 다시 적용하지 않는다. `--body FILE`은 서버 REST와 같은 JSON이고, `--query 'key=value&key=value'`는 URL 인코딩한 query다. 쓰기를 재시도할 때는 같은 opId를 포함한 파일을 사용한다.

`adocs help`에 명령 목록이 있다. read/open/create/edit/header/move/trash/untrash, history/revision/diff/restore/name-revision/restore-block, threads/thread/comment/resolve-thread/edit-comment/delete-comment/reaction/claim, changes, navigation/favorites/favorite/backlinks/statistics를 공통 REST에 연결한다. `revision`과 `name-revision`은 문서 UUID 다음에 리비전 번호를 받는다. 추가 REST 행동은 `adocs api METHOD /api/PATH --body FILE`로 사용할 수 있고 명령 추가는 `CommandRegistry`에서 한다. 정확한 필드와 권한은 서버 REST Docs를 따른다. CLI가 서버 업무 규칙을 별도로 재현하지 않는다.

첫 pull은 새 로컬 파일을 만든다. 기존 파일을 지정하면 거절한다. 모든 조각의 문서 ID·version이 일치해야 하고 cursor가 전진해야 한다. 원본은 `${XDG_CACHE_HOME:-$HOME/.cache}/adocs/<origin SHA-256>/<absolute file path SHA-256>.json`에 보관한다. front matter, `b:`·`h:`·`cols:` 주석과 표의 `r:` 주석을 유지한다. 새 블록은 주석 없이 의미 있는 마크다운 조각으로 쓴다. 블록 삭제는 주석과 본문을 함께 지운다. push는 바뀐 블록만 replace/insert/delete/move로 변환하고 원본 hash를 조건으로 한 `/edits` 요청 하나를 보낸다. 서버가 낡은 hash를 409로 거절한다.

front matter 변경은 전송 전에 `header_changed`로 거절한다. `adocs header DOCUMENT_UUID --body header.json`으로 별도 변경한다. 200연산을 넘으면 전송 전에 `too_many_operations`로 거절하므로 변경을 나누어 push한다. 표 열 주석을 손상시키거나 기존 블록 주석을 수정·복제하면 거절한다.

push 요청은 전송 전에 0600 `.pending.json`에 opId·본문·편집본을 기록한다. 응답 유실이나 성공 후 원본 갱신 실패가 나면 같은 파일로 `adocs push FILE`을 재실행한다. 새 요청을 만들지 않고 같은 opId를 사용한다. 응답과 새 원본도 저장한 뒤 로컬 파일·원본을 갱신하고 pending을 제거한다. 서버 응답을 받지 못했다면 편집본과 cache를 보존한 채 같은 push를 재시도한다. 문서 내용이 담긴 cache를 공개하거나 커밋하지 않는다.

서버가 `409 conflict`로 편집 묶음을 거절한 경우에는 다음처럼 같은 파일에서 계속한다.

1. 오류의 `conflicts`에서 블록 ID, 현재 해시와 본문을 확인한다.
2. `adocs pull DOCUMENT_UUID document.md`를 다시 실행한다. 현재 로컬 파일은 같은 디렉터리의 고유한 백업으로 옮기고, `document.md`에는 최신 서버 문서를 받는다. 출력의 `localEdits`가 백업 경로다.
3. 백업에서 반영할 내용을 골라 최신 `document.md`에 합친 뒤 `adocs push document.md`를 실행한다. 최신 블록 주석과 해시를 유지한다. 백업 파일에는 문서 본문이 들어 있으므로 커밋하지 않는다. 복구 중 중단되면 같은 pull을 재시도한다. 최초 백업 경로가 유지된다.

`adocs upload FILE --opId KEY`는 일반 첨부 업로드에 멱등 키를 전달한다. 응답을 받지 못하면 같은 키로 재시도하고 다른 업로드에는 새 키를 사용한다. 키를 생략하면 매번 새 첨부를 만든다.

import-folder는 폴더 파일들을 상대 경로와 함께 서버 가져오기 API로 전달한다. 각 파일은 서버가 created/failed/skipped로 응답하며 전체 폴더가 원자적으로 처리되지는 않는다. 이미 성공한 파일은 실패 시에도 남고 기존 문서를 덮어쓰지 않는다. symlink로 루트 밖을 읽지 않는다. 대형 폴더는 전송 전체를 메모리에 담으므로 나누어 실행한다. 전체 내보내기는 사람 소유자 전용 API이므로 에이전트 토큰 CLI 명령으로 제공하지 않는다.

검증은 `./gradlew :cli:test :cli:check :cli:installDist`로 수행한다. 저장소 전체 검증 절차는 루트 개발 문서를 따른다.
