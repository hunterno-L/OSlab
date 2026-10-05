"""本地开发回显沙箱 stub：模拟 RemoteCodeSandbox 协议的判题端点。

用途：学习闭环验证的 OJ 判题步骤需要一个可用的 remote 沙箱端点。
生产工厂只允许 remote 类型，本 stub 以"回显"语义（outputList = inputList）
提供稳定的本地判题端点，判题策略层真实比较用例产生 Accepted/WA。

启动：python scripts/dev/sandbox_stub.py   （监听 127.0.0.1:8090）
后端：CODESANDBOX_REMOTE_URL=http://127.0.0.1:8090 CODESANDBOX_REMOTE_AUTH_SECRET=smoke-secret
"""
import json
import os
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

HOST = "127.0.0.1"
PORT = int(os.environ.get("SANDBOX_PORT", "8090"))
AUTH_SECRET = os.environ.get("SANDBOX_AUTH_SECRET", "smoke-secret")
MAX_REQUEST_BYTES = 512 * 1024


def response_payload(status, message, judge_message, output_list=None, time_ms=0):
    return {
        "outputList": output_list or [],
        "message": message,
        "status": status,
        "judgeInfo": {"message": judge_message, "memory": 0, "time": time_ms},
    }


class Handler(BaseHTTPRequestHandler):
    def _send_json(self, status, payload):
        body = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        self._send_json(HTTPStatus.OK, {"status": 1, "message": "echo sandbox alive"})

    def do_POST(self):
        if AUTH_SECRET and self.headers.get("auth", "") != AUTH_SECRET:
            self._send_json(HTTPStatus.UNAUTHORIZED, response_payload(3, "auth failed", "Auth Failed"))
            return
        try:
            length = int(self.headers.get("Content-Length", "0"))
            if length <= 0 or length > MAX_REQUEST_BYTES:
                raise ValueError("bad length")
            body = json.loads(self.rfile.read(length).decode("utf-8"))
        except Exception:
            self._send_json(HTTPStatus.BAD_REQUEST, response_payload(3, "bad request", "Bad Request"))
            return
        input_list = body.get("inputList") or []
        output_list = [str(x) for x in input_list]
        self._send_json(HTTPStatus.OK, response_payload(1, "echo sandbox ok", "", output_list, 100))

    def log_message(self, _format, *_args):
        pass


if __name__ == "__main__":
    print(f"echo sandbox stub listening on http://{HOST}:{PORT}")
    ThreadingHTTPServer((HOST, PORT), Handler).serve_forever()
