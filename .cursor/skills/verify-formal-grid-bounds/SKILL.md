---
name: verify-formal-grid-bounds
description: Formal property and boundary verification for DeterministicMockGenerator time-series grid alignment, sliding-window continuity, current quantization, and 64-bit maxPointsPerSensor overflow bounds in mock-data-core. Use whenever editing DeterministicMockGenerator, GeneratorConfig, or timestamp parsing/alignment in MockSensorController.
---

# Verify `DeterministicMockGenerator` Grid Alignment & Bound Safety

This skill checks the mathematical and 64-bit integer safety promises of `DeterministicMockGenerator` (`mock-data-core`) directly against the compiled Java bytecode using a `BigInteger` mathematical oracle and generated/boundary inputs.

## Why This Tool

No Java 21/25 source or bytecode formal verifier (such as OpenJML, KeY, or JBMC) is installed in this workspace, and writing a separate hand-written model would not catch regressions in the actual Java code. Instead, [GridBoundsVerifier.java](./scripts/GridBoundsVerifier.java) compiles directly against `mock-data-core/build/classes/java/main` (requiring only the repo's existing JDK 21+ and Python 3) and drives the real `DeterministicMockGenerator` implementation, while [run_verify.py](./scripts/run_verify.py) isolates extreme 64-bit inputs in `-Xmx64m` timeout-guarded child JVMs so OOM/infinite-loop counterexamples are caught cleanly in seconds.

## Properties & Harm (Plain Language)

- **P1 — Point-Count Bound & Overflow Safety:** Every call to `generateTimeSeries` either rejects invalid input cleanly with `InvalidMockRequestException` (including whenever `floor((toEpochMs - fromEpochMs) / effectiveInterval) > config.maxPointsPerSensor()`) or returns at most `config.maxPointsPerSensor() + 1` inclusive grid points without throwing `IllegalArgumentException`, exhausting heap (`OutOfMemoryError`), or hanging. *Harm if broken:* A single HTTP `/timeseries` request with extreme `from`/`to` epoch timestamps can bypass `maxPointsPerSensor` and crash or hang the JVM (denial of service).
- **P2 — Grid Alignment & Window Inclusion:** Every timestamp `t` returned by `generateTimeSeries` lies within `[fromEpochMs, toEpochMs]`, is an exact multiple of `effectiveInterval`, advances by exact `effectiveInterval` steps, and omits no grid multiple inside the window. *Harm if broken:* Consumers receive misaligned timestamps or missing boundary samples.
- **P3 — Sliding-Window Continuity:** Two overlapping `[from, to]` queries with the same `sensorIds`, `intervalMs`, and `GeneratorConfig` return the exact same `(timestamp, value)` pairs across their intersection. *Harm if broken:* Scrolling a chart window causes historical mock sensor values to jump or flicker.
- **P4 — Current Quantization & Series Consistency:** `generateCurrentValues` either rejects unrepresentable 64-bit floor underflow (`floor(nowEpochMs / effectiveInterval) * effectiveInterval < Long.MIN_VALUE`) with `InvalidMockRequestException` or quantizes `nowEpochMs` to the greatest grid multiple `q <= nowEpochMs` (`nowEpochMs - q < effectiveInterval`), and `generateTimeSeries` over `[q, q + effectiveInterval]` returns the identical `value` and `pattern` at `q`. *Harm if broken:* Live `/current` readings wrap to the far future (`+9.22e18`) or disagree with `/timeseries` at the same timestamp.

## Coverage Record & Sync Commit

- **Verified sync commit:** `5965a9761cdea206368b2ca45d4e818649a99fc0`
- **Covered files and methods:**
  - [DeterministicMockGenerator.java](../../../mock-data-core/src/main/java/com/samsung/ees/infra/mock/core/engine/DeterministicMockGenerator.java): `generateCurrentValues`, `generateTimeSeries`, `resolveInterval`, `quantizeCurrent`, `buildAlignedTimestamps`
  - [GeneratorConfig.java](../../../mock-data-core/src/main/java/com/samsung/ees/infra/mock/core/config/GeneratorConfig.java): `minIntervalMs`, `maxPointsPerSensor`, `defaultCurrentIntervalMs`, `defaultSeriesIntervalMs`
  - [DeterministicWaveCalculator.java](../../../mock-data-core/src/main/java/com/samsung/ees/infra/mock/core/engine/DeterministicWaveCalculator.java): `prepare`, `calculate` (exercised transitively for value/pattern consistency in P3 and P4)

### Checking Whether a Change Touches Verified Code

Run `git diff --name-only 5965a9761cdea206368b2ca45d4e818649a99fc0..HEAD` (or include working tree changes with `git diff --name-only 5965a9761cdea206368b2ca45d4e818649a99fc0`). If any file listed above changed, re-run the check below before merging or moving the sync commit forward.

## Running the Check

```powershell
python .cursor/skills/verify-formal-grid-bounds/scripts/run_verify.py
```

- Builds `:mock-data-core:classes` via `gradlew.bat` (or `./gradlew` on POSIX), compiles `GridBoundsVerifier.java` into `build/formal-verify/`, and runs both the in-process property suite and the `-Xmx64m` subprocess extreme-case suite.
- Exit code `0` means zero findings across all properties. Exit code `2` (or non-zero) means at least one property failed and prints JSON `findings` with exact Java inputs.
- Pass `--skip-build` only when `mock-data-core` classfiles are already up to date.

## Scope, Bounds & Assumptions

- **Tested input bounds:**
  - `P1` off-by-one boundary: 4 aligned boundary sizes (`maxPointsPerSensor` = `1`, `5`, `100`, `10000`).
  - `P1` extreme 64-bit subprocess cases: 7 targeted `Long.MIN_VALUE` / `Long.MAX_VALUE` / signed-overflow pairs + 8 seeded random 64-bit `[Long.MIN_VALUE, Long.MAX_VALUE]` intervals (`2.0s` timeout, `64 MB` heap).
  - `P2` grid alignment & inclusion: 9 fixed boundary `fromEpochMs` values + 1,991 seeded random windows (`intervalMs` in `[1, 120000]`, span up to `50 * intervalMs`).
  - `P3` sliding-window continuity: 1,000 seeded random overlapping window pairs.
  - `P4` current quantization & consistency: 13 boundary `nowEpochMs` values (including `Long.MIN_VALUE`, `Long.MAX_VALUE`, `-1`, `0`) + 1,000 seeded random 64-bit timestamps.
- **Not covered:** Unbounded proof over all $2^{64} \times 2^{64}$ pairs (random + boundary sampling only); HTTP string timestamp parsing in `MockSensorController`; `DeterministicWaveCalculator` floating-point distribution or `NaN`/`Infinity` checks under custom extreme `SensorPhysicsPreset` values.

## Real Bug vs. Harness Error

- Because `GridBoundsVerifier` invokes the real `DeterministicMockGenerator` against `java.math.BigInteger` exact arithmetic, a `FINDING` indicates a real discrepancy in the production Java code unless you intentionally changed the contract (for example, changing open/closed interval inclusivity on `toEpochMs` or changing `maxPointsPerSensor` semantics).
- Compilation errors (`javac`) or unexpected exceptions in `GridBoundsVerifier.java` itself indicate the public Java signature of `DeterministicMockGenerator` or `GeneratorConfig` changed and the harness needs updating.

## Current Status

All four properties (`P1`–`P4`, 4,032 total suite + subprocess cases) pass with `0` findings after replacing signed subtraction `(toEpochMs - fromEpochMs) / effectiveInterval` with unsigned 64-bit `Long.divideUnsigned`, merging overflow-safe ceiling alignment into `buildAlignedTimestamps`, and guarding `quantizeCurrent` against `Long.MIN_VALUE` underflow.

## Updating This Skill

When an intended code change fixes any baseline finding or changes grid/bound rules:

1. Update the property or oracle in [GridBoundsVerifier.java](./scripts/GridBoundsVerifier.java) / [run_verify.py](./scripts/run_verify.py) only if the intended specification changed (never weaken a property just to silence a bug).
2. Run `python .cursor/skills/verify-formal-grid-bounds/scripts/run_verify.py` and confirm the expected pass/finding state.
3. Update the sync commit hash in this `SKILL.md` **only after** confirming the verification accurately describes the code at that commit.
