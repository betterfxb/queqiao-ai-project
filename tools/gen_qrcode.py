#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
生成「信息采集页」访问二维码。

用法：
    python tools/gen_qrcode.py --url "http://1.2.3.4/h5/" --out deploy/h5-qrcode.png

说明：
    - 二维码内容即采集页地址（nginx 的 /h5/ 路由）。
    - 拿到云服务器公网 IP 或域名后，重跑本脚本替换 URL 即可（无需改代码）。
    - 默认输出到 deploy/h5-qrcode.png，可打印/嵌入宣传物料。
"""
import argparse
import sys

try:
    import qrcode
except ImportError:
    sys.exit("请先安装依赖：pip install qrcode[pil]")


def gen(url: str, out: str, size: int = 6, border: int = 2):
    qr = qrcode.QRCode(
        version=None,
        error_correction=qrcode.constants.ERROR_CORRECT_M,  # 中等容错，可加 logo
        box_size=size,
        border=border,
    )
    qr.add_data(url)
    qr.make(fit=True)
    img = qr.make_image(fill_color="#8B254A", back_color="white")  # 勃艮第酒红
    img.save(out)
    print(f"二维码已生成：{out}")
    print(f"  内容：{url}")
    print(f"  尺寸：{img.size[0]}x{img.size[1]} px")


def main():
    ap = argparse.ArgumentParser(description="生成采集页二维码")
    ap.add_argument(
        "--url",
        default="http://<服务器IP>/h5/",
        help="采集页完整地址，例如 http://1.2.3.4/h5/ 或 https://match.queqiao.com/h5/",
    )
    ap.add_argument("--out", default="deploy/h5-qrcode.png", help="输出 PNG 路径")
    ap.add_argument("--size", type=int, default=8, help="每个模块像素大小")
    ap.add_argument("--border", type=int, default=3, help="安静区模块数")
    args = ap.parse_args()
    gen(args.url, args.out, args.size, args.border)


if __name__ == "__main__":
    main()
