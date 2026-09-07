"""Private HTTP API that launches constrained, disposable Docker workers."""

import base64
import hmac
import json
import os
import subprocess
import tempfile
import threading
import time
import uuid
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

HOST = "0.0.0.0"
PORT = int(os.environ.get("SANDBOX_PORT", "8090"))
AUTH_SECRET = os.environ.get("SANDBOX_AUTH_SECRET", "")
RUNNER_IMAGE = os.environ.get("SANDBOX_RUNNER_IMAGE", "oslab-judge-sandbox:local")
MAX_CONCURRENT_WORKERS = max(1, int(os.environ.get("SANDBOX_MAX_CONCURRENT", "2")))
WORKER_SEMAPHORE = threading.BoundedSemaphore(MAX_CONCURRENT_WORKERS)
MAX_SOURCE_BYTES = 64 * 1024
MAX_INPUT_BYTES = 8 * 1024
MAX_CASES = 20
MAX_REQUEST_BYTES = 512 * 1024
MAX_TIME_MS = 5000
MAX_MEMORY_MB = 512
DEFAULT_TIME_MS = 1000
DEFAULT_MEMORY_MB = 256


def response_payload(status, message, judge_message, output_list=None, time_ms=0):
    return {
        "outputList": output_list or [],
        "message": message,
        "status": status,
        "judgeInfo": {
            "message": judge_message,
            "memory": 0,
            "time": time_ms,
        },
    }


def clamp(value, default, minimum, maximum):
    if not isinstance(value, int):
        return default
    return max(minimum, min(value, maximum))


def remove_worker(cid_path):
    """Best-effort cleanup also covers a client timeout before --rm runs."""
    try:
        with open(cid_path, "r", encoding="utf-8") as cid_file:
            container_id = cid_file.read().strip()
        if container_id:
            subprocess.run(
                ["docker", "rm", "--force", container_id],
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
                check=False,
                timeout=10,
            )
    except (FileNotFoundError, OSError, subprocess.SubprocessError):
        pass


def execute_worker(payload):
    language = payload["language"]
    source = payload["code"]
    inputs = payload["inputList"]
    time_limit = clamp(payload.get("timeLimit"), DEFAULT_TIME_MS, 50, MAX_TIME_MS)
    memory_limit = clamp(payload.get("memoryLimit"), DEFAULT_MEMORY_MB, 32, MAX_MEMORY_MB)
    request_id = uuid.uuid4().hex
    cid_path = os.path.join(tempfile.gettempdir(), "%s.cid" % request_id)
    source_b64 = base64.b64encode(source.encode("utf-8")).decode("ascii")
    inputs_b64 = base64.b64encode(json.dumps(inputs).encode("utf-8")).decode("ascii")
    command = [
        "docker", "run", "--rm", "--cidfile", cid_path,
        "--name", "oslab-judge-%s" % request_id,
        "--label", "com.oslab.sandbox.worker=true",
        "--network", "none",
        "--read-only",
        "--tmpfs", "/work:rw,exec,nosuid,nodev,size=64m,mode=1777",
        "--tmpfs", "/tmp:rw,exec,nosuid,nodev,size=16m,mode=1777",
        "--workdir", "/work",
        "--user", "65534:65534",
        "--memory", "%sm" % memory_limit,
        "--memory-swap", "%sm" % memory_limit,
        "--cpus", "1",
        "--pids-limit", "64",
        "--ulimit", "nofile=64:64",
        "--cap-drop", "ALL",
        "--security-opt", "no-new-privileges:true",
        "--env", "HOME=/work",
        "--env", "OSLAB_CODE_B64=%s" % source_b64,
        "--env", "OSLAB_INPUTS_B64=%s" % inputs_b64,
        "--env", "OSLAB_TIME_LIMIT_MS=%s" % time_limit,
        "--env", "OSLAB_MEMORY_LIMIT_MB=%s" % memory_limit,
        "--entrypoint", "python3",
        RUNNER_IMAGE,
        "/app/runner.py",
        language,
    ]
    # Compilation is inside the worker. Allow a small fixed overhead and one
    # execution window per case, but never let a single request live forever.
    timeout_seconds = min(30, 5 + (time_limit * len(inputs)) / 1000.0)
    started = time.monotonic()
    process = None
    try:
        process = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        stdout, _ = process.communicate(timeout=timeout_seconds)
        elapsed = int((time.monotonic() - started) * 1000)
        if process.returncode == 137:
            return response_payload(3, "memory limit exceeded", "Memory Limit Exceeded", time_ms=elapsed)
        if process.returncode != 0:
            return response_payload(2, "sandbox worker failed", "System Error", time_ms=elapsed)
        try:
            result = json.loads(stdout.decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError):
            return response_payload(2, "sandbox worker returned an invalid response", "System Error", time_ms=elapsed)
        return result
    except subprocess.TimeoutExpired:
        if process is not None:
            process.kill()
            process.communicate()
        remove_worker(cid_path)
        return response_payload(3, "time limit exceeded", "Time Limit Exceeded", time_ms=int(timeout_seconds * 1000))
    except (OSError, subprocess.SubprocessError):
        return response_payload(2, "sandbox worker unavailable", "System Error")
    finally:
        remove_worker(cid_path)
        try:
            os.remove(cid_path)
        except FileNotFoundError:
            pass


class SandboxHandler(BaseHTTPRequestHandler):
    def send_json(self, status, payload):
        raw = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(raw)))
        self.end_headers()
        self.wfile.write(raw)

    def do_GET(self):
        if self.path != "/health":
            self.send_error(HTTPStatus.NOT_FOUND)
            return
        self.send_json(HTTPStatus.OK, {"status": "ok"})

    def do_POST(self):
        if self.path != "/executeCode":
            self.send_error(HTTPStatus.NOT_FOUND)
            return
        provided_secret = self.headers.get("auth", "")
        if not AUTH_SECRET or not hmac.compare_digest(provided_secret, AUTH_SECRET):
            self.send_error(HTTPStatus.FORBIDDEN)
            return
        try:
            content_length = int(self.headers.get("Content-Length", "0"))
        except ValueError:
            content_length = 0
        if content_length <= 0 or content_length > MAX_REQUEST_BYTES:
            self.send_json(HTTPStatus.BAD_REQUEST, response_payload(2, "invalid request size", "System Error"))
            return
        try:
            payload = json.loads(self.rfile.read(content_length).decode("utf-8"))
            if not isinstance(payload, dict):
                raise ValueError
            source = payload.get("code")
            language = payload.get("language")
            inputs = payload.get("inputList")
            if language not in {"java", "c", "cpp", "go"} or not isinstance(source, str):
                raise ValueError
            if len(source.encode("utf-8")) > MAX_SOURCE_BYTES:
                raise ValueError
            if not isinstance(inputs, list) or not inputs or len(inputs) > MAX_CASES:
                raise ValueError
            if any(not isinstance(item, str) or len(item.encode("utf-8")) > MAX_INPUT_BYTES for item in inputs):
                raise ValueError
        except (UnicodeDecodeError, ValueError, json.JSONDecodeError):
            self.send_json(HTTPStatus.BAD_REQUEST, response_payload(2, "invalid executeCode request", "System Error"))
            return
        if not WORKER_SEMAPHORE.acquire(timeout=30):
            self.send_json(HTTPStatus.SERVICE_UNAVAILABLE,
                           response_payload(2, "sandbox is busy", "System Error"))
            return
        try:
            result = execute_worker(payload)
        finally:
            WORKER_SEMAPHORE.release()
        self.send_json(HTTPStatus.OK, result)

    def log_message(self, _format, *_args):
        # Never write submitted code, input, or shared credentials to logs.
        return


if __name__ == "__main__":
    ThreadingHTTPServer((HOST, PORT), SandboxHandler).serve_forever()
