## 언어 및 기록 규칙

### 한글 사용

- 사용자와의 모든 대화 응답과 작업 기록은 **한글**로 작성한다.
- 코드, CLI 명령어, 파일 경로, 심볼명, 설정 키 등 고유 식별자는 원형을 그대로 유지한다.

### 대화 기록 자동 요약 (`agent-log.md`)

- 매 대화 턴(작업 단위)을 마무리할 때마다 저장소 루트의 `agent-log.md`에 대화 내용을 요약하여 **순차적으로 누적 기록(append)** 한다. 기존 기록은 수정하거나 삭제하지 않는다.
- 병렬 서브에이전트/워크트리 작업 시에는 티켓 브랜치에서 `agent-log.md`를 수정하지 않고, 최상위 코디네이터가 사용자 턴 마무리 시점에 일괄 기록한다.
- `agent-log.md`가 없으면 새로 생성한다.
- 각 항목은 아래 형식을 따른다:
  - `## [YYYY-MM-DD HH:mm] <요약 제목>`
  - **요청**: 사용자의 요청 요약
  - **수행 내용**: 확인/변경/실행한 주요 작업 및 결과
  - **변경 파일**: 생성·수정된 파일 목록 (없으면 `없음`)

## Repo & Verification Pointers

- **Modules**: `mock-data-core` (pure Java engine), `mock-data-spring-boot-starter` (Spring Boot 3.5.x auto-config & web layer), `example-app` (consumer demo).
- **Verify command**: `.\gradlew.bat clean check` (Windows) / `./gradlew clean check` (POSIX); `publishToMavenLocal` for local Maven verification.
- **Coding standards (review)**: `CODING_STANDARDS.md`
- **Windows git worktrees**: Place worktrees under a short root (e.g. `C:\Users\imoil\repo\_wt\<branch>`) to avoid Windows `MAX_PATH` failures on deep Java package/resource paths.

## Agent skills

### Issue tracker

Issues and specs are tracked as GitHub Issues in `imoil/mock-data-gen` using the `gh` CLI. See `docs/agents/issue-tracker.md`.

### Triage labels

Default five canonical triage labels (`needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`). See `docs/agents/triage-labels.md`.

### Domain docs

Single-context layout (`GLOSSARY.md` and `docs/adr/` at the repo root). See `docs/agents/domain.md`.
