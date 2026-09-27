#!/usr/bin/env python3
"""Exercise the built image and supplied Compose service with an installed MyStem.

Requires Python 3.10+, Docker and Compose 2.24.4+ (ports override support).
No binary download or image publication is performed here.
"""

import argparse
import concurrent.futures
import http.client
import json
import os
from pathlib import Path
import secrets
import subprocess
import tempfile
import time
import uuid


ROOT = Path(__file__).resolve().parent.parent
MODES = {
    "oneshot": ("ONE_SHOT_PROCESS_PER_REQUEST", "ONE_SHOT_TEXT"),
    "session": ("REUSABLE_SESSION", "SESSION"),
    "pooled": ("POOLED_SESSIONS", "POOL"),
}


def run(*args, env=None, check=True):
    result = subprocess.run(args, cwd=ROOT, env=env, text=True, capture_output=True,
                            timeout=60, check=False)
    if check and result.returncode:
        raise RuntimeError(f"Command failed: {args[0]}\n{result.stdout}\n{result.stderr}")
    return result.stdout.strip()


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def request(port, token, path, body=None, media_type=None):
    connection = http.client.HTTPConnection("127.0.0.1", port, timeout=15)
    headers = {}
    if token:
        headers["Authorization"] = "Bearer " + token
    if media_type:
        headers["Content-Type"] = media_type
    try:
        connection.request("POST" if body is not None else "GET", path, body, headers)
        response = connection.getresponse()
        return response.status, dict(response.getheaders()), response.read()
    finally:
        connection.close()


def check_analysis(response, mode, raw=False):
    status, headers, body = response
    headers = {name.lower(): value for name, value in headers.items()}
    require(status == 200, f"Analysis status: {status}")
    require(headers.get("x-mystem-mode") == mode, f"Unexpected native mode: {headers}")
    require(headers.get("x-mystem-version") == "1", "Missing protocol version")
    require(headers.get("x-mystem-format") == "JSON", "Unexpected native format")
    output = body.decode("utf-8") if raw else json.loads(body)["output"]
    require('"lex":"кошка"' in output or '"lex": "кошка"' in output,
            f"Missing real native analysis: {output}")
    return output


def check_mode(binary, mode, profile, text_mode):
    with tempfile.TemporaryDirectory(prefix="mystem-docker-smoke-") as directory:
        directory = Path(directory)
        # The container uses UID 10001; the generated smoke-only token must be readable.
        directory.chmod(0o755)
        token = secrets.token_hex(24)
        token_file = directory / "token"
        token_file.write_text(token, encoding="utf-8")
        token_file.chmod(0o444)
        override = directory / "override.yaml"
        override.write_text(f"""services:
  mystem:
    ports: !override
      - "127.0.0.1::8080"
    environment:
      MYSTEM_MODE: {mode}
      MYSTEM_TOKEN_FILE: /run/secrets/mystem-token
    volumes:
      - type: bind
        source: {json.dumps(str(token_file))}
        target: /run/secrets/mystem-token
        read_only: true
""", encoding="utf-8")
        environment = dict(os.environ, MYSTEM_BINARY=str(binary))
        compose = ("docker", "compose", "--project-name", "mystem-smoke-" + uuid.uuid4().hex,
                   "-f", str(ROOT / "docker/compose.yaml"), "-f", str(override))
        container = None
        try:
            run(*compose, "up", "-d", "--no-build", env=environment)
            container = run(*compose, "ps", "-q", "mystem", env=environment)
            inspected = json.loads(run("docker", "inspect", container))[0]
            require(inspected["Config"]["User"] == "10001:10001", "Container must run unprivileged")
            require(inspected["HostConfig"]["ReadonlyRootfs"], "Root filesystem must be read-only")
            require(inspected["HostConfig"]["Init"], "Compose must enable init for signal handling")
            require("/tmp" in inspected["HostConfig"]["Tmpfs"], "Missing temporary filesystem")
            native_mount = next(m for m in inspected["Mounts"] if m["Destination"] == "/opt/mystem/mystem")
            require(not native_mount["RW"], "Native binary must be mounted read-only")
            binding = inspected["NetworkSettings"]["Ports"]["8080/tcp"][0]
            require(binding["HostIp"] == "127.0.0.1", "Host port must bind only to loopback")
            port = int(binding["HostPort"])
            deadline = time.monotonic() + 30
            while True:
                try:
                    if request(port, None, "/health/live")[0] == 204:
                        break
                except (OSError, http.client.HTTPException):
                    pass
                require(time.monotonic() < deadline, "Service did not become live within 30 seconds")
                time.sleep(0.2)
            require(request(port, None, "/v1/info")[0] == 401, "Metadata must require authentication")
            status, headers, _ = request(port, token, "/v1/info")
            require(status == 204, "Authenticated metadata failed")
            require({k.lower(): v for k, v in headers.items()}.get("x-mystem-profile") == profile,
                    f"Unexpected execution profile in {mode}")
            body = json.dumps({"text": "Кошки спят."}, ensure_ascii=False).encode("utf-8")
            def analyze(_):
                check_analysis(request(port, token, "/v1/analyze", body, "application/json"), text_mode)
            with concurrent.futures.ThreadPoolExecutor(max_workers=4) as executor:
                list(executor.map(analyze, range(8)))
            file_body = "Кошки спят.\nСобаки едят.".encode("utf-8")
            captured = check_analysis(request(port, token, "/v1/files/content", file_body,
                                              "application/octet-stream"), "ONE_SHOT_FILE")
            direct = check_analysis(request(port, token, "/v1/files/output", file_body,
                                            "application/octet-stream"), "ONE_SHOT_FILE", raw=True)
            require(captured == direct and "собака" in direct, "File methods must preserve multiline analysis")
            deadline = time.monotonic() + 5
            while run("docker", "exec", container, "find", "/tmp", "-name", "mystem-http-*"):
                require(time.monotonic() < deadline, "Request temporary files leaked")
                time.sleep(0.1)
            run(*compose, "stop", env=environment)
            state = json.loads(run("docker", "inspect", container))[0]["State"]
            require(not state["Running"] and not state["OOMKilled"], "Container did not stop cleanly")
            require(state["ExitCode"] in (0, 143), f"Shutdown failed or needed SIGKILL: {state}")
            print(f"PASS {mode}: authenticated text/files, concurrency, filesystem and SIGTERM", flush=True)
        except Exception:
            if container:
                print(run("docker", "logs", container, check=False), flush=True)
            raise
        finally:
            run(*compose, "down", "--volumes", env=environment)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("executable", type=Path, help="Installed Linux x64 MyStem executable")
    args = parser.parse_args()
    binary = args.executable.resolve(strict=True)
    require(binary.is_file() and os.access(binary, os.X_OK), "MyStem must be an executable file")
    with binary.open("rb") as source:
        header = source.read(20)
    require(header[:6] == b"\x7fELF\x02\x01" and header[18:20] == b"\x3e\x00",
            "MyStem must be a Linux x64 ELF binary")
    for mode, (profile, text_mode) in MODES.items():
        check_mode(binary, mode, profile, text_mode)


if __name__ == "__main__":
    main()
