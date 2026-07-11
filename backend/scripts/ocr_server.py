"""
PaddleOCR HTTP 服务 - 供 Java 后端调用
启动: python ocr_server.py
监听: http://127.0.0.1:8866
"""
import json
import os
import sys
import tempfile
from http.server import HTTPServer, BaseHTTPRequestHandler

try:
    from paddleocr import PaddleOCR
except ImportError:
    print("ERROR: PaddleOCR 未安装，请运行: pip install paddleocr")
    sys.exit(1)

# 全局单例，模型只加载一次
ocr = PaddleOCR(use_angle_cls=True, lang='ch')


class OCRHandler(BaseHTTPRequestHandler):
    def do_POST(self):
        try:
            content_length = int(self.headers.get('Content-Length', 0))
            if content_length == 0:
                self._send_error(400, 'Empty request')
                return

            body = self.rfile.read(content_length)
            tmp_path = None
            try:
                # 保存到临时文件
                with tempfile.NamedTemporaryFile(suffix='.png', delete=False) as f:
                    f.write(body)
                    tmp_path = f.name

                # PaddleOCR 识别
                result = ocr.ocr(tmp_path, cls=True)
                lines = []
                if result and result[0]:
                    for line in result[0]:
                        lines.append(line[1][0])
                text = '\n'.join(lines)

                self._send_json(200, {'text': text, 'success': True})
            finally:
                if tmp_path and os.path.exists(tmp_path):
                    os.unlink(tmp_path)
        except Exception as e:
            self._send_json(500, {'text': '', 'success': False, 'error': str(e)})

    def do_GET(self):
        """健康检查"""
        self._send_json(200, {'status': 'ok'})

    def _send_json(self, code, data):
        body = json.dumps(data, ensure_ascii=False).encode('utf-8')
        self.send_response(code)
        self.send_header('Content-Type', 'application/json; charset=utf-8')
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _send_error(self, code, msg):
        self._send_json(code, {'text': '', 'success': False, 'error': msg})

    def log_message(self, format, *args):
        """抑制默认日志，避免干扰"""
        pass


if __name__ == '__main__':
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8866
    server = HTTPServer(('127.0.0.1', port), OCRHandler)
    print(f'PaddleOCR server started on http://127.0.0.1:{port}')
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print('\nServer stopped.')
        server.server_close()