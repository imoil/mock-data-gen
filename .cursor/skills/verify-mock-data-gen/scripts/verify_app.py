#!/usr/bin/env python3
"""Self-contained lifecycle, health, HTTP request, and cleanup helper for verifying mock-data-gen (example-app)."""

from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import platform
import shutil
import signal
import socket
import subprocess
import sys
import time
import urllib.error
import urllib.request


def find_repo_root() -> Path:
    here = Path(__file__).resolve()
    for parent in here.parents:
        if (parent / "settings.gradle").is_file() and (parent / "example-app").is_dir():
            return parent
    raise RuntimeError("Could not locate mock-data-gen repository root from script path")


REPO_ROOT = find_repo_root()
RUNS_ROOT = REPO_ROOT / "build" / "verify-runs"
EVIDENCE_ROOT = REPO_ROOT / "artifacts" / "verify-mock-data-gen"


def utc_now_iso() -> str:
    return dt.datetime.now(dt.timezone.utc).isoformat().replace("+00:00", "Z")


def run_dir_for(run_id: str) -> Path:
    if not run_id or any(c in run_id for c in ("/", "\\", "..")):
        raise ValueError(f"Invalid run-id: {run_id!r}")
    return RUNS_ROOT / run_id


def evidence_dir_for(run_id: str) -> Path:
    return EVIDENCE_ROOT / run_id


def is_windows() -> bool:
    return platform.system().lower().startswith("win")


def is_pid_alive(pid: int) -> bool:
    if pid <= 0:
        return False
    if is_windows():
        proc = subprocess.run(
            ["tasklist", "/FI", f"PID eq {pid}", "/FO", "CSV", "/NH"],
            capture_output=True,
            text=True,
            check=False,
        )
        for line in proc.stdout.splitlines():
            stripped = line.strip()
            if stripped.startswith('"') and f'"{pid}"' in stripped.split(",")[1:2]:
                return True
        return False
    try:
        os.kill(pid, 0)
        return True
    except ProcessLookupError:
        return False
    except PermissionError:
        return True


def kill_pid_only(pid: int, timeout_sec: float = 5.0) -> bool:
    """Terminates only the exact recorded PID (and its child tree on Windows). Never kills by process name."""
    if not is_pid_alive(pid):
        return True
    if is_windows():
        subprocess.run(
            ["taskkill", "/PID", str(pid), "/T", "/F"],
            capture_output=True,
            text=True,
            check=False,
        )
    else:
        try:
            os.kill(pid, signal.SIGTERM)
        except ProcessLookupError:
            return True
        deadline = time.monotonic() + timeout_sec
        while time.monotonic() < deadline:
            if not is_pid_alive(pid):
                return True
            time.sleep(0.2)
        try:
            os.kill(pid, signal.SIGKILL)
        except ProcessLookupError:
            return True

    deadline = time.monotonic() + timeout_sec
    while time.monotonic() < deadline:
        if not is_pid_alive(pid):
            return True
        time.sleep(0.2)
    return not is_pid_alive(pid)


def find_free_port() -> int:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
        sock.bind(("127.0.0.1", 0))
        return int(sock.getsockname()[1])


def is_port_open(port: int) -> bool:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
        sock.settimeout(0.5)
        return sock.connect_ex(("127.0.0.1", port)) == 0


def find_listening_pids(port: int) -> list[int] | None:
    """Returns listening PIDs for localhost port if OS tooling is available, or None if indeterminate."""
    if is_windows():
        proc = subprocess.run(
            [
                "powershell",
                "-NoProfile",
                "-Command",
                f"Get-NetTCPConnection -LocalPort {port} -State Listen -ErrorAction SilentlyContinue | Select-Object -ExpandProperty OwningProcess",
            ],
            capture_output=True,
            text=True,
            check=False,
        )
        if proc.returncode == 0:
            pids = []
            for line in proc.stdout.splitlines():
                line = line.strip()
                if line.isdigit():
                    val = int(line)
                    if val > 0:
                        pids.append(val)
            return pids or None
        return None
    if shutil.which("lsof"):
        proc = subprocess.run(
            ["lsof", f"-tiTCP:{port}", "-sTCP:LISTEN"],
            capture_output=True,
            text=True,
            check=False,
        )
        if proc.returncode in (0, 1):
            return [int(line.strip()) for line in proc.stdout.splitlines() if line.strip().isdigit()]
    return None


def http_get(url: str, headers: dict[str, str] | None = None, timeout: float = 5.0) -> tuple[int, dict[str, str], str]:
    req = urllib.request.Request(url, method="GET", headers=headers or {})
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            body = resp.read().decode("utf-8", errors="replace")
            resp_headers = {k: v for k, v in resp.headers.items()}
            return int(resp.status), resp_headers, body
    except urllib.error.HTTPError as err:
        body = err.read().decode("utf-8", errors="replace")
        resp_headers = {k: v for k, v in err.headers.items()} if err.headers else {}
        return int(err.code), resp_headers, body


def resolve_boot_jar() -> Path:
    libs_dir = REPO_ROOT / "example-app" / "build" / "libs"
    candidates = sorted(
        p for p in libs_dir.glob("example-app-*.jar") if not p.name.endswith("-plain.jar")
    )
    if not candidates:
        raise FileNotFoundError(f"No bootJar found under {libs_dir}")
    return candidates[-1]


def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(65536), b""):
            h.update(chunk)
    return h.hexdigest()


def parse_app_args(raw_args: list[str]) -> dict[str, str]:
    parsed: dict[str, str] = {}
    for item in raw_args:
        if "=" not in item:
            raise ValueError(f"--app-arg must be key=value, got {item!r}")
        key, value = item.split("=", 1)
        parsed[key.strip()] = value.strip()
    return parsed


def build_boot_jar() -> None:
    gradlew = str(REPO_ROOT / ("gradlew.bat" if is_windows() else "gradlew"))
    proc = subprocess.run(
        [gradlew, ":example-app:bootJar", "--console=plain"],
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
        raise RuntimeError(f":example-app:bootJar failed with exit code {proc.returncode}")


def cmd_launch(args: argparse.Namespace) -> int:
    run_dir = run_dir_for(args.run_id)
    state_file = run_dir / "state.json"
    if state_file.is_file():
        existing = json.loads(state_file.read_text(encoding="utf-8"))
        existing_pid = int(existing.get("pid", -1))
        if is_pid_alive(existing_pid):
            print(
                json.dumps(
                    {
                        "error": f"Run '{args.run_id}' is already active (pid={existing_pid}, port={existing.get('port')}). Run cleanup first or use another --run-id."
                    },
                    indent=2,
                ),
                file=sys.stderr,
            )
            return 1

    app_args = parse_app_args(args.app_arg or [])
    if not args.skip_build:
        build_boot_jar()

    jar_path = resolve_boot_jar()
    jar_sha = sha256_file(jar_path)
    port = args.port if args.port and args.port > 0 else find_free_port()
    if is_port_open(port):
        print(json.dumps({"error": f"Port {port} is already in use"}, indent=2), file=sys.stderr)
        return 1

    base_path = app_args.get("ees.mock.sensor.base-path", "/api/v1/sensors")
    current_path = app_args.get("ees.mock.sensor.current-path", "/current")
    timeseries_path = app_args.get("ees.mock.sensor.timeseries-path", "/timeseries")
    mock_enabled = app_args.get("ees.mock.sensor.enabled", "true").lower() == "true"

    run_dir.mkdir(parents=True, exist_ok=True)
    evidence_dir = evidence_dir_for(args.run_id)
    evidence_dir.mkdir(parents=True, exist_ok=True)

    stdout_path = run_dir / "stdout.log"
    stderr_path = run_dir / "stderr.log"

    java_cmd = [
        "java",
        "-jar",
        str(jar_path),
        f"--server.port={port}",
        f"--spring.profiles.active={args.profile}",
    ] + [f"--{k}={v}" for k, v in app_args.items()]

    popen_kwargs: dict[str, object] = {}
    if is_windows():
        popen_kwargs["creationflags"] = subprocess.CREATE_NEW_PROCESS_GROUP | subprocess.DETACHED_PROCESS
    else:
        popen_kwargs["start_new_session"] = True

    with stdout_path.open("w", encoding="utf-8") as out_f, stderr_path.open("w", encoding="utf-8") as err_f:
        proc = subprocess.Popen(
            java_cmd,
            cwd=str(REPO_ROOT),
            stdin=subprocess.DEVNULL,
            stdout=out_f,
            stderr=err_f,
            **popen_kwargs,  # type: ignore[arg-type]
        )

    pid = proc.pid
    base_url = f"http://127.0.0.1:{port}"
    probe_url = f"{base_url}{base_path}{current_path}?sensorIds=READY-PROBE"
    docs_url = f"{base_url}/v3/api-docs"

    deadline = time.monotonic() + args.timeout
    ready = False
    last_err = ""
    while time.monotonic() < deadline:
        if proc.poll() is not None:
            last_err = f"Process exited prematurely with code {proc.returncode}"
            break
        log_text = stdout_path.read_text(encoding="utf-8", errors="replace") if stdout_path.exists() else ""
        if "Started ExampleApplication in" in log_text:
            try:
                docs_status, _, _ = http_get(docs_url, timeout=2.0)
                probe_status, _, probe_body = http_get(
                    probe_url, headers={"X-Mock-Data": "true"}, timeout=2.0
                )
                if docs_status == 200 and probe_status == 200 and '"mock"' in probe_body:
                    ready = True
                    break
                last_err = f"docs_status={docs_status}, probe_status={probe_status}"
            except Exception as exc:  # noqa: BLE001
                last_err = str(exc)
        time.sleep(0.25)

    if not ready:
        kill_pid_only(pid)
        out_tail = stdout_path.read_text(encoding="utf-8", errors="replace")[-2000:] if stdout_path.exists() else ""
        err_tail = stderr_path.read_text(encoding="utf-8", errors="replace")[-2000:] if stderr_path.exists() else ""
        print(
            json.dumps(
                {
                    "error": f"Application failed readiness check: {last_err}",
                    "stdoutTail": out_tail,
                    "stderrTail": err_tail,
                },
                indent=2,
            ),
            file=sys.stderr,
        )
        return 1

    state = {
        "runId": args.run_id,
        "pid": pid,
        "port": port,
        "baseUrl": base_url,
        "profile": args.profile,
        "mockEnabled": mock_enabled,
        "basePath": base_path,
        "currentPath": current_path,
        "timeseriesPath": timeseries_path,
        "jarPath": str(jar_path),
        "jarSha256": jar_sha,
        "appArgs": app_args,
        "startedAt": utc_now_iso(),
        "stdoutLog": str(stdout_path),
        "stderrLog": str(stderr_path),
        "evidenceDir": str(evidence_dir),
    }
    state_file.write_text(json.dumps(state, indent=2), encoding="utf-8")
    print(json.dumps(state, indent=2))
    return 0


def cmd_doctor(args: argparse.Namespace) -> int:
    run_dir = run_dir_for(args.run_id)
    state_file = run_dir / "state.json"
    if not state_file.is_file():
        print(json.dumps({"healthy": False, "error": f"No state.json found at {state_file}"}, indent=2))
        return 1

    state = json.loads(state_file.read_text(encoding="utf-8"))
    pid = int(state["pid"])
    port = int(state["port"])
    base_url = state["baseUrl"]
    profile = state["profile"]
    mock_enabled = bool(state.get("mockEnabled", True))
    base_path = state["basePath"]
    current_path = state["currentPath"]
    stdout_log = Path(state["stdoutLog"])

    checks: dict[str, object] = {}
    ok = True

    pid_alive = is_pid_alive(pid)
    checks["pidAlive"] = {"ok": pid_alive, "pid": pid}
    ok = ok and pid_alive

    listening_pids = find_listening_pids(port)
    if listening_pids is None:
        port_owned = is_port_open(port)
        checks["portOwnership"] = {"ok": port_owned, "port": port, "mode": "tcp-connect-fallback"}
    else:
        port_owned = pid in listening_pids
        checks["portOwnership"] = {"ok": port_owned, "port": port, "listeningPids": listening_pids}
    ok = ok and port_owned

    jar_path = Path(state["jarPath"])
    jar_matches = jar_path.is_file() and sha256_file(jar_path) == state["jarSha256"]
    checks["jarIntegrity"] = {"ok": jar_matches, "jarPath": str(jar_path)}
    ok = ok and jar_matches

    log_text = stdout_log.read_text(encoding="utf-8", errors="replace") if stdout_log.is_file() else ""
    log_started = "Started ExampleApplication in" in log_text and f"Tomcat started on port {port}" in log_text
    profile_logged = (
        f'"{profile}"' in log_text
        or f"profile is active: \"{profile}\"" in log_text
        or (profile == "local" and 'default profile: "local"' in log_text)
    )
    checks["startupLog"] = {"ok": log_started and profile_logged, "started": log_started, "profileMatched": profile_logged}
    ok = ok and log_started and profile_logged

    expected_mock_active = profile in ("local", "dev") and mock_enabled
    try:
        docs_status, _, docs_body = http_get(f"{base_url}/v3/api-docs", timeout=3.0)
        docs_json = json.loads(docs_body)
        full_current_path = f"{base_path}{current_path}"
        has_route = full_current_path in docs_json.get("paths", {})
        params = (
            docs_json.get("paths", {})
            .get(full_current_path, {})
            .get("get", {})
            .get("parameters", [])
        )
        has_mock_header_doc = any(p.get("name") == "X-Mock-Data" for p in params)
        docs_ok = docs_status == 200 and has_route and (has_mock_header_doc == expected_mock_active)
        checks["openApi"] = {
            "ok": docs_ok,
            "status": docs_status,
            "routePresent": has_route,
            "xMockDataDocumented": has_mock_header_doc,
            "expectedMockActive": expected_mock_active,
        }
    except Exception as exc:  # noqa: BLE001
        docs_ok = False
        checks["openApi"] = {"ok": False, "error": str(exc)}
    ok = ok and docs_ok

    try:
        probe_status, _, probe_body = http_get(
            f"{base_url}{base_path}{current_path}?sensorIds=DOCTOR-PROBE",
            headers={"X-Mock-Data": "true"},
            timeout=3.0,
        )
        probe_json = json.loads(probe_body)
        probe_ok = probe_status == 200 and (probe_json.get("mock") is expected_mock_active)
        checks["probeEndpoint"] = {
            "ok": probe_ok,
            "status": probe_status,
            "mock": probe_json.get("mock"),
            "expectedMock": expected_mock_active,
        }
    except Exception as exc:  # noqa: BLE001
        probe_ok = False
        checks["probeEndpoint"] = {"ok": False, "error": str(exc)}
    ok = ok and probe_ok

    report = {
        "healthy": ok,
        "runId": args.run_id,
        "baseUrl": base_url,
        "profile": profile,
        "evidenceDir": state["evidenceDir"],
        "checks": checks,
    }
    print(json.dumps(report, indent=2))
    return 0 if ok else 1


def cmd_request(args: argparse.Namespace) -> int:
    run_dir = run_dir_for(args.run_id)
    state_file = run_dir / "state.json"
    if not state_file.is_file():
        print(json.dumps({"error": f"No active state.json at {state_file}"}, indent=2), file=sys.stderr)
        return 1

    state = json.loads(state_file.read_text(encoding="utf-8"))
    if not is_pid_alive(int(state["pid"])):
        print(json.dumps({"error": f"Instance pid={state['pid']} is not running"}, indent=2), file=sys.stderr)
        return 1

    req_headers: dict[str, str] = {}
    for raw_header in args.header or []:
        if ":" not in raw_header:
            raise ValueError(f"--header must be 'Name: Value', got {raw_header!r}")
        name, val = raw_header.split(":", 1)
        req_headers[name.strip()] = val.strip()

    path = args.path if args.path.startswith("/") else f"/{args.path}"
    url = f"{state['baseUrl']}{path}"
    status, resp_headers, raw_body = http_get(url, headers=req_headers, timeout=5.0)

    try:
        parsed_body: object = json.loads(raw_body)
    except json.JSONDecodeError:
        parsed_body = raw_body

    record = {
        "runId": args.run_id,
        "profile": state["profile"],
        "port": state["port"],
        "capturedAt": utc_now_iso(),
        "request": {
            "method": "GET",
            "url": url,
            "headers": req_headers,
        },
        "response": {
            "status": status,
            "headers": resp_headers,
            "body": parsed_body,
        },
    }

    if args.save:
        rel_path = Path(args.save)
        if rel_path.is_absolute() or ".." in rel_path.parts:
            raise ValueError(f"--save must be a safe relative path inside evidenceDir, got {args.save!r}")
        target = Path(state["evidenceDir"]) / rel_path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(json.dumps(record, indent=2), encoding="utf-8")
        record["savedTo"] = str(target)

    print(json.dumps(record, indent=2))
    if args.expected_status is not None and status != args.expected_status:
        return 1
    return 0


def cleanup_single_run(run_dir: Path) -> dict[str, object]:
    run_id = run_dir.name
    state_file = run_dir / "state.json"
    pid: int | None = None
    port: int | None = None
    evidence_dir = evidence_dir_for(run_id)
    if state_file.is_file():
        state = json.loads(state_file.read_text(encoding="utf-8"))
        pid = int(state["pid"])
        port = int(state["port"])
        evidence_dir = Path(state["evidenceDir"])

    terminated = True
    if pid is not None:
        terminated = kill_pid_only(pid)

    shutil.rmtree(run_dir, ignore_errors=True)
    evidence_files = (
        sum(1 for p in evidence_dir.rglob("*") if p.is_file()) if evidence_dir.is_dir() else 0
    )
    return {
        "runId": run_id,
        "pid": pid,
        "port": port,
        "terminated": terminated,
        "runtimeDirRemoved": not run_dir.exists(),
        "evidenceDir": str(evidence_dir),
        "evidencePreservedFiles": evidence_files,
    }


def cmd_cleanup(args: argparse.Namespace) -> int:
    results = []
    if args.all:
        if RUNS_ROOT.is_dir():
            for child in sorted(RUNS_ROOT.iterdir()):
                if child.is_dir():
                    results.append(cleanup_single_run(child))
    else:
        run_dir = run_dir_for(args.run_id)
        if run_dir.exists():
            results.append(cleanup_single_run(run_dir))
        else:
            evidence_dir = evidence_dir_for(args.run_id)
            evidence_files = (
                sum(1 for p in evidence_dir.rglob("*") if p.is_file()) if evidence_dir.is_dir() else 0
            )
            results.append(
                {
                    "runId": args.run_id,
                    "pid": None,
                    "port": None,
                    "terminated": True,
                    "runtimeDirRemoved": True,
                    "evidenceDir": str(evidence_dir),
                    "evidencePreservedFiles": evidence_files,
                }
            )
    print(json.dumps({"cleaned": results}, indent=2))
    return 0 if all(bool(r["terminated"]) for r in results) else 1


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Verification harness for mock-data-gen (example-app)")
    sub = parser.add_subparsers(dest="command", required=True)

    p_launch = sub.add_parser("launch", help="Build (unless --skip-build) and launch an isolated example-app instance")
    p_launch.add_argument("--run-id", required=True, help="Unique identifier for this verification run")
    p_launch.add_argument("--profile", default="local", choices=["local", "dev", "prod"], help="Spring profile")
    p_launch.add_argument("--port", type=int, default=0, help="TCP port (0 = auto-select free port)")
    p_launch.add_argument("--skip-build", action="store_true", help="Reuse existing example-app bootJar")
    p_launch.add_argument("--timeout", type=float, default=45.0, help="Readiness timeout in seconds")
    p_launch.add_argument("--app-arg", action="append", default=[], help="Extra Spring Boot property as key=value")
    p_launch.set_defaults(func=cmd_launch)

    p_doctor = sub.add_parser("doctor", help="Read-only health check for a launched verification instance")
    p_doctor.add_argument("--run-id", required=True)
    p_doctor.set_defaults(func=cmd_doctor)

    p_req = sub.add_parser("request", help="Send a GET request to the running instance and optionally save proof")
    p_req.add_argument("--run-id", required=True)
    p_req.add_argument("--path", required=True, help="Request path and query string, e.g. /api/v1/sensors/current?sensorIds=TEMP-01")
    p_req.add_argument("--header", action="append", default=[], help="HTTP header as 'Name: Value'")
    p_req.add_argument("--save", help="Relative path inside evidenceDir to store the captured request+response JSON")
    p_req.add_argument("--expected-status", type=int, help="Fail non-zero if HTTP status differs")
    p_req.set_defaults(func=cmd_request)

    p_clean = sub.add_parser("cleanup", help="Stop only the tracked PID and remove runtime scratch (preserves evidence)")
    grp = p_clean.add_mutually_exclusive_group(required=True)
    grp.add_argument("--run-id")
    grp.add_argument("--all", action="store_true")
    p_clean.set_defaults(func=cmd_cleanup)

    return parser


def main() -> int:
    parser = build_parser()
    args = parser.parse_args()
    return int(args.func(args))


if __name__ == "__main__":
    raise SystemExit(main())
