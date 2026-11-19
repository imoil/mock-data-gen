# Sensor Mock Data

상태 저장(DB·캐시) 없이 `SensorId`와 시각만으로 결정론적인 센서 측정값과 시계열 데이터를 생성하는 도메인.

## Language

**SensorId**:
측정 대상 센서를 고유하게 식별하는 문자열 키.
_Avoid_: `sensorNo`, `deviceId`, `tagId`

**Sensor Seed**:
`SensorId`로부터 결정론적으로 도출되는 64비트 고유 해시값으로, 해당 센서의 파형 패턴과 물리 파라미터 및 노이즈 시퀀스를 영구 고정하는 기준값.
_Avoid_: `randomSeed`, `sessionKey`

**Pattern**:
`Sensor Seed`에 의해 센서에 고정 배정되는 5가지 신호 변동 모델(`STEADY`, `SINE`, `STEP`, `DRIFT`, `ANOMALY`).
_Avoid_: `waveType`, `signalMode`, `profile`

**Current Quantization**:
현재 값 조회 시 기준 시각을 `intervalMs` 단위로 내림 정렬하여 동일 주기 내 재호출 일관성과 시계열 연속성을 보장하는 시각 정렬 규칙.
_Avoid_: `grid alignment`

**Grid Alignment**:
시계열 조회 시 시작 시각을 `intervalMs`의 정수 배수 격자로 올림 정렬하여 조회 범위가 이동하더라도 중첩 구간의 샘플링 타임스탬프를 유지하는 규칙.
_Avoid_: `quantization`

**Mock Request Header (`X-Mock-Data`)**:
동일 엔드포인트에서 실제 운영 데이터 조회와 Mock 데이터 조회를 요청 단위로 분기하는 HTTP 헤더 식별자.
