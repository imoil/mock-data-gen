# 0002. 결정론적 노이즈 입력 통일 및 Starter 설정 커스터마이징

Status: accepted

`프로젝트사양서.md` 4.2절 수식(`doubleToLongBits(t_sec)`)과 6.2절 레퍼런스 코드(`sensorSeed ^ timestampMs`) 간의 노이즈 해시 입력 불일치를 6.2절 기준으로 통일하고, Starter 설정 프리픽스(`app-custom-config.mock.sensor.*`) 및 엔드포인트 URI를 외부 프로젝트 규칙에 맞게 변경할 수 있도록 설계한다.

## Considered Options

- **노이즈 입력으로 4.2절(`doubleToLongBits(t_sec)`) 채택 vs 6.2절(`timestampMs` long) 채택**: 레퍼런스 코드와 테스트 기대값의 직접 일치 및 부동소수점 변환 없는 정수 비트 연산 단순성을 위해 `timestampMs` 기반(`mix64(sensorSeed ^ timestampMs)`)으로 통일한다.
- **고정 `app.mock.sensor.*` 및 고정 URI vs `app-custom-config.mock.sensor.*` 및 URI 분리 설정**: 사양서 예시의 `/api/v1/sensors/current`, `/timeseries` 경로는 기본값으로 유지하되, 외부 프로젝트가 각자의 URI 컨벤션에 맞게 `base-path`, `current-path`, `timeseries-path`를 오버라이드할 수 있도록 분리한다.

## Consequences

- 사양서 4.2절과 6.2절을 대조하는 독자는 노이즈 해시가 밀리초(`long timestampMs`) 입력을 사용함을 본 ADR로 확인할 수 있다.
- 모든 Starter 프로퍼티는 `app-custom-config.mock.sensor.*` 네임스페이스 아래에서 관리된다.
