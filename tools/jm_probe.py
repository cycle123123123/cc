#!/usr/bin/env python3
"""JMComic3 协议参考实现（可执行版）。

依赖: pip install pycryptodome pillow

用法:
    python jm_probe.py hosts                 # 拉取并打印线路列表
    python jm_probe.py setting               # 应用配置
    python jm_probe.py latest [page]         # 最新上架
    python jm_probe.py album <aid>           # 作品详情
    python jm_probe.py chapter <aid>         # 章节信息
    python jm_probe.py read <cid>            # 图片列表 (含 scramble_id)
    python jm_probe.py image <aid> <cid> <page> <out.png>   # 下载并还原指定页

这个文件同时充当 §6 图片还原算法的可参考实现。
"""
import base64
import hashlib
import io
import json
import os
import ssl
import sys
import time
import urllib.request

try:
    from Crypto.Cipher import AES
except ImportError:
    sys.exit("缺少 pycryptodome: pip install pycryptodome pillow")

sys.stdout.reconfigure(encoding="utf-8")

VERSION = "2.1.13"
TOKEN_SECRET = "185Hcomic3PAPP7R"
CONTENT_SECRET = "18comicAPPContent"
HOST_LIST_KEY = "diosfjckwpqpdfjkvnqQjsik"

SERVER_LIST_URLS = [
    "https://rup4a04-c02.tos-cn-hongkong.bytepluses.com/newsvr-2025.txt",
    "https://rup4a04-c01.tos-ap-southeast-1.bytepluses.com/newsvr-2025.txt",
    "https://rup4a04-c03.tos-cn-beijing.bytepluses.com.cn/newsvr-2025.txt",
]

FALLBACK_HOSTS = ["www.cdnbtd.cc", "www.cdnutc.cc", "www.cdngwc.net", "www.cdngwc.club"]

SSL_CTX = ssl.create_default_context()
SSL_CTX.check_hostname = False
SSL_CTX.verify_mode = ssl.CERT_NONE

UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0 Mobile Safari/537.36"
IMG_HEADERS = {"User-Agent": UA, "Referer": "https://18comic.vip/"}


def md5(text: str) -> str:
    return hashlib.md5(text.encode("utf-8")).hexdigest()


def aes_ecb_decrypt(data: bytes, key: bytes) -> bytes:
    cipher = AES.new(key, AES.MODE_ECB)
    padded = cipher.decrypt(data)
    return padded[: -padded[-1]] if padded else padded


def fetch(url: str, timeout: int = 25, headers: dict | None = None) -> bytes:
    req = urllib.request.Request(url, headers=headers or {"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=timeout, context=SSL_CTX) as resp:
        return resp.read()


# ---------------------------------------------------------------- 线路

def fetch_hosts() -> list[str]:
    """拉取加密线路清单并解密。失败返回内置兜底列表。"""
    for url in SERVER_LIST_URLS:
        try:
            raw = fetch(url).decode("utf-8-sig").strip()
            plain = aes_ecb_decrypt(base64.b64decode(raw), md5(HOST_LIST_KEY).encode())
            data = json.loads(plain.decode("utf-8"))
            hosts = [item[0] for item in data.get("jm3_Server", [])] or data.get("Server", [])
            if hosts:
                return hosts
        except Exception as exc:  # 单个地址失败就换下一个
            print(f"[warn] {url} -> {exc}", file=sys.stderr)
    return FALLBACK_HOSTS


# ---------------------------------------------------------------- API

class JmApi:
    def __init__(self, base: str):
        self.base = base.rstrip("/") + "/"

    def signed_headers(self, ts: int) -> dict:
        return {
            "Token": md5(f"{ts}{TOKEN_SECRET}"),
            "Tokenparam": f"{ts},{VERSION}",
            "Accept": "application/json",
            "User-Agent": UA,
            "Referer": "https://18comic.vip/",
        }

    def decrypt_data(self, data_b64: str, ts: int):
        raw = base64.b64decode(data_b64)
        for secret in (TOKEN_SECRET, CONTENT_SECRET):
            try:
                plain = aes_ecb_decrypt(raw, md5(f"{ts}{secret}").encode())
                return json.loads(plain.decode("utf-8"))
            except Exception:
                continue
        raise ValueError("响应解密失败（时间戳或密钥不匹配）")

    def get(self, path: str, **params):
        ts = int(time.time())
        query = {"lang": "TW", **params}
        url = self.base + path + "?" + "&".join(f"{k}={v}" for k, v in query.items())
        body = fetch(url, headers=self.signed_headers(ts)).decode("utf-8", "replace")
        envelope = json.loads(body)
        if envelope.get("code") != 200:
            return envelope.get("code"), None
        return 200, self.decrypt_data(envelope["data"], ts)


def connect() -> JmApi:
    for host in fetch_hosts():
        api = JmApi(f"https://{host}/")
        try:
            code, _ = api.get("setting")
            if code == 200:
                print(f"[host] {host}", file=sys.stderr)
                return api
        except Exception as exc:
            print(f"[warn] {host} -> {exc}", file=sys.stderr)
    raise SystemExit("没有可用线路")


# ---------------------------------------------------------------- 图片还原

def segment_count(aid: int, key: str) -> int:
    last = md5(f"{aid}{key}")[-1]
    r = ord(last)
    if 268850 <= aid <= 421925:
        r %= 10
    elif aid >= 421926:
        r %= 8
    return {0: 2, 1: 4, 2: 6, 3: 8, 4: 10, 5: 12, 6: 14, 7: 16, 8: 18, 9: 20}.get(r, 10)


def unscramble(image, aid: int, scramble_id: int, key: str):
    """image: PIL.Image。返回还原后的 Image。"""
    from PIL import Image

    if aid < scramble_id:
        return image

    width, height = image.size
    t = segment_count(aid, key)
    if t <= 1:
        return image

    h, u = divmod(height, t)
    src_bounds = []
    y = 0
    for _ in range(t - 1):
        src_bounds.append((y, y + h))
        y += h
    src_bounds.append((y, height))

    dst_bounds = []
    y = 0
    dst_bounds.append((y, y + h + u))
    y += h + u
    for _ in range(t - 1):
        dst_bounds.append((y, y + h))
        y += h

    out = Image.new(image.mode, (width, height))
    for (sy0, sy1), (dy0, dy1) in zip(reversed(src_bounds), dst_bounds):
        out.paste(image.crop((0, sy0, width, sy1)), (0, dy0))
    return out


def image_key(url: str, page: int) -> str:
    name = url.split("?")[0].rsplit("/", 1)[-1]
    return os.path.splitext(name)[0] or str(page)


# ---------------------------------------------------------------- CLI

def main(argv: list[str]) -> None:
    if len(argv) < 2:
        print(__doc__)
        return

    cmd = argv[1]
    if cmd == "hosts":
        for host in fetch_hosts():
            print(host)
        return

    api = connect()

    if cmd == "setting":
        print(json.dumps(api.get("setting")[1], ensure_ascii=False, indent=2))
    elif cmd == "latest":
        page = argv[2] if len(argv) > 2 else 1
        print(json.dumps(api.get("latest", page=page)[1], ensure_ascii=False, indent=2))
    elif cmd == "album":
        print(json.dumps(api.get("album", id=argv[2])[1], ensure_ascii=False, indent=2))
    elif cmd == "chapter":
        print(json.dumps(api.get("chapter", id=argv[2])[1], ensure_ascii=False, indent=2))
    elif cmd == "read":
        print(json.dumps(api.get("comic_read", id=argv[2])[1], ensure_ascii=False, indent=2))
    elif cmd == "image":
        from PIL import Image

        aid, cid, page, out_path = int(argv[2]), argv[3], int(argv[4]), argv[5]
        data = api.get("comic_read", id=cid)[1]
        entry = data["images"][page - 1]
        url = entry["image"]
        raw = fetch(url, headers=IMG_HEADERS)
        img = Image.open(io.BytesIO(raw))
        key = image_key(url, page)
        fixed = unscramble(img.convert("RGB"), aid, int(data["scramble_id"]), key)
        fixed.save(out_path)
        print(f"saved {out_path}  aid={aid} scramble_id={data['scramble_id']} key={key} t={segment_count(aid, key)}")
    else:
        print(__doc__)


if __name__ == "__main__":
    main(sys.argv)
