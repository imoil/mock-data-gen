## 언어 및 기록 규칙

### 한글 사용

- 사용자와의 모든 대화 응답과 작업 기록은 **한글**로 작성한다.
- 코드, CLI 명령어, 파일 경로, 심볼명, 설정 키 등 고유 식별자는 원형을 그대로 유지한다.

### 대화 기록 자동 요약 (`agent-log.md`)

- 매 대화 턴(작업 단위)을 마무리할 때마다 저장소 루트의 `agent-log.md`에 대화 내용을 요약하여 **순차적으로 누적 기록(append)** 한다. 기존 기록은 수정하거나 삭제하지 않는다.
- `agent-log.md`가 없으면 새로 생성한다.
- 각 항목은 아래 형식을 따른다:
  - `## [YYYY-MM-DD HH:mm] <요약 제목>`
  - **요청**: 사용자의 요청 요약
  - **수행 내용**: 확인/변경/실행한 주요 작업 및 결과
  - **변경 파일**: 생성·수정된 파일 목록 (없으면 `없음`)

## Agent skills

### Issue tracker

Issues and specs are tracked as GitHub Issues in `imoil/mock-data-gen` using the `gh` CLI. See `docs/agents/issue-tracker.md`.

### Triage labels

Default five canonical triage labels (`needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`). See `docs/agents/triage-labels.md`.

### Domain docs

Single-context layout (`GLOSSARY.md` and `docs/adr/` at the repo root). See `docs/agents/domain.md`.
