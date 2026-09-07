"""Executes one submitted program inside an already constrained worker."""

import base64
import json
import os
import signal
import subprocess
import sys
import time

WORK_DIR = "/work"
MAX_OUTPUT_BYTES = 64 * 1024


def payload(status, message, judge_message, outputs=None, time_ms=0):
    print(json.dumps({
        "outputList": outputs or [],
        "message": message,
        "status": status,
        "judgeInfo": {"message": judge_message, "memory": 0, "time": time_ms},
    }, ensure_ascii=False))


def run_command(command, stdin_text, timeout_seconds):
    process = subprocess.Popen(
        command,
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        cwd=WORK_DIR,
        start_new_session=True,
        env=dict(os.environ, HOME=WORK_DIR, TMPDIR=WORK_DIR, GOCACHE=os.path.join(WORK_DIR, "go-cache")),
    )
    try:
        stdout, stderr = process.communicate(stdin_text.encode("utf-8"), timeout=timeout_seconds)
    except subprocess.TimeoutExpired:
        os.killpg(process.pid, signal.SIGKILL)
        process.communicate()
        return None, None, None, True
    if len(stdout) + len(stderr) > MAX_OUTPUT_BYTES:
        return None, None, None, False
    return process.returncode, stdout.decode("utf-8", "replace"), stderr.decode("utf-8", "replace"), False


def language_commands(language, memory_limit):
    if language == "java":
        return ["javac", "-encoding", "UTF-8", "Main.java"], ["java", "-Djava.io.tmpdir=/work", "-Xmx%sm" % max(16, memory_limit - 32), "-cp", WORK_DIR, "Main"]
    if language == "c":
        return ["gcc", "-O2", "-pipe", "main.c", "-o", "main"], ["./main"]
    if language == "cpp":
        return ["g++", "-std=c++17", "-O2", "-pipe", "main.cpp", "-o", "main"], ["./main"]
    if language == "go":
        return ["go", "build", "-o", "main", "main.go"], ["./main"]
    raise ValueError("unsupported language")


def source_filename(language):
    return {"java": "Main.java", "c": "main.c", "cpp": "main.cpp", "go": "main.go"}[language]


def main():
    if len(sys.argv) != 2:
        payload(2, "invalid worker command", "System Error")
        return
    try:
        language = sys.argv[1]
        source = base64.b64decode(os.environ["OSLAB_CODE_B64"], validate=True).decode("utf-8")
        inputs = json.loads(base64.b64decode(os.environ["OSLAB_INPUTS_B64"], validate=True).decode("utf-8"))
        time_limit_ms = int(os.environ["OSLAB_TIME_LIMIT_MS"])
        memory_limit_mb = int(os.environ["OSLAB_MEMORY_LIMIT_MB"])
        compile_command, run_command_base = language_commands(language, memory_limit_mb)
    except (KeyError, ValueError, UnicodeDecodeError, json.JSONDecodeError):
        payload(2, "invalid worker input", "System Error")
        return

    with open(os.path.join(WORK_DIR, source_filename(language)), "w", encoding="utf-8") as source_file:
        source_file.write(source)
    compile_exit, _, compile_error, compile_timed_out = run_command(compile_command, "", 5)
    if compile_timed_out:
        payload(3, "compilation timed out", "Compile Error")
        return
    if compile_exit != 0:
        payload(3, (compile_error or "compilation failed")[:4096], "Compile Error")
        return

    outputs = []
    maximum_time = 0
    for item in inputs:
        started = time.monotonic()
        exit_code, stdout, stderr, timed_out = run_command(
            run_command_base + item.split(), item, time_limit_ms / 1000.0)
        elapsed = int((time.monotonic() - started) * 1000)
        maximum_time = max(maximum_time, elapsed)
        if timed_out:
            payload(3, "time limit exceeded", "Time Limit Exceeded", outputs, maximum_time)
            return
        if exit_code is None:
            payload(3, "output limit exceeded", "Output Limit Exceeded", outputs, maximum_time)
            return
        if exit_code != 0:
            payload(3, (stderr or "runtime error")[:4096], "Runtime Error", outputs, maximum_time)
            return
        outputs.append(stdout)
    payload(1, "", "Accepted", outputs, maximum_time)


if __name__ == "__main__":
    main()
