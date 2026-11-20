#!/usr/bin/env python3
"""Compiles and runs the formal property harness directly against compiled mock-data-core bytecode."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import platform
import random
import subprocess
import sys


def find_repo_root() -> Path:
    here = Path(__file__).resolve()
    for parent in here.parents:
        if (parent / "settings.gradle").is_file() and (parent / "mock-data-core").is_dir():
            return parent
    raise RuntimeError("Could not locate mock-data-gen repository root")


REPO_ROOT = find_repo_root()
SCRIPT_DIR = Path(__file__).resolve().parent
CORE_CLASSES = REPO_ROOT / "mock-data-core" / "build" / "classes" / "java" / "main"
HARNESS_OUT = REPO_ROOT / "build" / "formal-verify"
HARNESS_SRC = SCRIPT_DIR / "GridBoundsVerifier.java"

LONG_MIN = -(1 << 63)
LONG_MAX = (1 << 63) - 1

EXTREME_CASES: list[tuple[str, int, int, int, int]] = [
    ("signed-diff-overflow-symmetric", -5_000_000_000_000_000_000, 5_000_000_000_000_000_000, 1000, 10000),
    ("signed-diff-overflow-min-to-zero", LONG_MIN, 0, 1000, 10000),
    ("signed-diff-overflow-full-range", LONG_MIN, LONG_MAX, 1000, 10000),
    ("alignGridStart-overflow-near-max", LONG_MAX - 5, LONG_MAX, 1000, 10000),
    ("alignGridStart-near-min", LONG_MIN, LONG_MIN + 500, 1000, 10000),
    ("loop-top-boundary-max", LONG_MAX - 1000, LONG_MAX, 1000, 10000),
    ("interval-near-max", 0, LONG_MAX, LONG_MAX, 10),
]


def is_windows() -> bool:
    return platform.system().lower().startswith("win")


def build_core_and_harness() -> None:
    gradlew = str(REPO_ROOT / ("gradlew.bat" if is_windows() else "gradlew"))
    proc = subprocess.run(
        [gradlew, ":mock-data-core:classes", "--console=plain"],
        cwd=str(REPO_ROOT),
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        check=False,
    )
    if proc.returncode != 0:
        sys.stderr.write(proc.stdout)
        sys.stderr.write(proc.stderr)
        raise RuntimeError(f":mock-data-core:classes failed with exit code {proc.returncode}")

    HARNESS_OUT.mkdir(parents=True, exist_ok=True)
    jc = subprocess.run(
        ["javac", "-cp", str(CORE_CLASSES), "-d", str(HARNESS_OUT), str(HARNESS_SRC)],
        cwd=str(REPO_ROOT),
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        check=False,
    )
    if jc.returncode != 0:
        sys.stderr.write(jc.stdout)
        sys.stderr.write(jc.stderr)
        raise RuntimeError(f"javac failed with exit code {jc.returncode}")


def classpath() -> str:
    sep = ";" if is_windows() else ":"
    return f"{HARNESS_OUT}{sep}{CORE_CLASSES}"


def run_suite() -> list[dict[str, str]]:
    proc = subprocess.run(
        ["java", "-Xmx128m", "-cp", classpath(), "GridBoundsVerifier"],
        cwd=str(REPO_ROOT),
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        timeout=30,
        check=False,
    )
    findings: list[dict[str, str]] = []
    for line in proc.stdout.splitlines():
        if line.startswith("FINDING|"):
            _, prop, summary, ce = line.split("|", 3)
            findings.append({"property": prop, "summary": summary, "counterexample": ce})
    if proc.returncode not in (0, 2):
        raise RuntimeError(f"Suite exited unexpectedly ({proc.returncode}): {proc.stderr}")
    return findings


def run_extreme_case(name: str, frm: int, to: int, interval: int, max_pts: int, timeout_sec: float) -> dict[str, str] | None:
    try:
        proc = subprocess.run(
            [
                "java",
                "-Xmx64m",
                "-cp",
                classpath(),
                "GridBoundsVerifier",
                "--mode=single-case",
                str(frm),
                str(to),
                str(interval),
                str(max_pts),
            ],
            cwd=str(REPO_ROOT),
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
            timeout=timeout_sec,
            check=False,
        )
    except subprocess.TimeoutExpired:
        return {
            "property": "P1",
            "case": name,
            "summary": f"JVM timed out after {timeout_sec}s (non-terminating / huge loop)",
            "counterexample": f"fromEpochMs={frm}, toEpochMs={to}, intervalMs={interval}, maxPointsPerSensor={max_pts}",
        }

    for line in proc.stdout.splitlines():
        if line.startswith("FAIL|"):
            _, prop, summary, ce = line.split("|", 3)
            return {"property": prop, "case": name, "summary": summary, "counterexample": ce}

    if proc.returncode != 0:
        err_line = (proc.stderr or proc.stdout).strip().splitlines()[-1] if (proc.stderr or proc.stdout).strip() else f"exit={proc.returncode}"
        return {
            "property": "P1",
            "case": name,
            "summary": f"JVM crashed ({err_line})",
            "counterexample": f"fromEpochMs={frm}, toEpochMs={to}, intervalMs={interval}, maxPointsPerSensor={max_pts}",
        }
    return None


def main() -> int:
    parser = argparse.ArgumentParser(description="Run formal property verification against mock-data-core")
    parser.add_argument("--skip-build", action="store_true", help="Skip Gradle compilation (only reuse existing classfiles)")
    parser.add_argument("--timeout", type=float, default=2.0, help="Per-extreme-case JVM timeout in seconds")
    args = parser.parse_args()

    if not args.skip_build:
        build_core_and_harness()

    suite_findings = run_suite()

    extreme_cases = list(EXTREME_CASES)
    rng = random.Random(0x5EED_64B17)
    for idx in range(8):
        a = rng.randint(LONG_MIN, LONG_MAX - 1)
        b = rng.randint(a + 1, LONG_MAX)
        iv = rng.choice([100, 1000, 10_000, 60_000])
        extreme_cases.append((f"random-64bit-{idx}", a, b, iv, 10000))

    extreme_findings: list[dict[str, str]] = []
    for name, frm, to, iv, max_pts in extreme_cases:
        res = run_extreme_case(name, frm, to, iv, max_pts, args.timeout)
        if res is not None:
            extreme_findings.append(res)

    all_findings = suite_findings + extreme_findings
    report = {
        "target": "com.samsung.ees.infra.mock.core.engine.DeterministicMockGenerator",
        "propertiesChecked": ["P1 (maxPointsPerSensor & overflow safety)", "P2 (grid alignment & inclusion)", "P3 (sliding-window continuity)", "P4 (current quantization & series consistency)"],
        "suiteCases": {
            "P1_offByOneBoundary": 4,
            "P2_gridAlignment": 2000,
            "P3_slidingContinuity": 1000,
            "P4_currentConsistency": 1013,
            "P1_extreme64BitSubprocess": len(extreme_cases),
        },
        "totalFindings": len(all_findings),
        "findings": all_findings,
    }
    print(json.dumps(report, indent=2))
    return 0 if not all_findings else 2


if __name__ == "__main__":
    raise SystemExit(main())
