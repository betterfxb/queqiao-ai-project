#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
sync_cities.py —— 行政区划数据同步器（数据维护脚本）

职责：
  以 queqiao-h5-collect/cities.json 为唯一发布数据源（single source of truth），
  同步到以下落点，保证前端两处 HTML 与后端校验数据完全一致：
    1. queqiao-h5-collect/index.html           —— 替换 `const CITIES = __CITIES__;`
    2. queqiao-server-java/.../static/collect.html  —— 替换 `const CITIES = __CITIES__;`
    3. queqiao-server-java/.../resources/cities.json  —— 直接复制
    4. queqiao-server-java/.../target/classes/...      —— 复制（dev 运行期 classpath）

用法：
  python sync_cities.py            # 全量同步
  python sync_cities.py --check    # 仅校验四处数据是否一致，不写文件
  python sync_cities.py --skip-classes  # 不同步 target/classes（部署时由 maven 处理）

说明：
  - 两个 HTML 使用统一锚点 `const CITIES = __CITIES__;`，
    __CITIES__ 会被替换为压缩后的 JSON 字面量。
  - 执行后后端 dev 运行需重新 `mvn -o process-resources`（target/classes 已同步则无需）。
"""
import argparse
import json
import pathlib
import re
import sys

HERE = pathlib.Path(__file__).resolve().parent
ROOT = HERE.parent  # queqiao 工作区根目录 d:/ai_project

PUBLISH = HERE / "cities.json"
H5_INDEX = HERE / "index.html"
BACKEND_RES_HTML = ROOT / "queqiao-server-java" / "src" / "main" / "resources" / "static" / "collect.html"
BACKEND_RES_JSON = ROOT / "queqiao-server-java" / "src" / "main" / "resources" / "cities.json"
BACKEND_CLS_HTML = ROOT / "queqiao-server-java" / "target" / "classes" / "static" / "collect.html"
BACKEND_CLS_JSON = ROOT / "queqiao-server-java" / "target" / "classes" / "cities.json"

ANCHOR = "const CITIES = __CITIES__;"
ANCHOR_RE = re.compile(re.escape("const CITIES = __CITIES__;"))


def read_publish() -> dict:
    if not PUBLISH.exists():
        print(f"[错误] 发布数据不存在: {PUBLISH}（请先运行 gen_cities.py）", file=sys.stderr)
        sys.exit(1)
    return json.loads(PUBLISH.read_text(encoding="utf-8"))


def publish_compact(data: dict) -> str:
    return json.dumps(data, ensure_ascii=False, separators=(",", ":"))


def inject_html(path: pathlib.Path, compact: str) -> bool:
    """把锚点替换为 const CITIES = <compact>; 返回是否发生写入。"""
    if not path.exists():
        print(f"[警告] 跳过不存在的文件: {path}")
        return False
    txt = path.read_text(encoding="utf-8")
    if "__CITIES__" not in txt:
        # 防御：若锚点丢失（被人手改回内联），拒绝盲目写入
        if "const CITIES = {" in txt:
            print(f"[错误] {path} 仍含内联 CITIES，锚点丢失，请恢复 `const CITIES = __CITIES__;`")
            sys.exit(1)
        print(f"[提示] {path} 无锚点也无内联，跳过")
        return False
    new = ANCHOR_RE.sub(f"const CITIES = {compact};", txt, count=1)
    if new == txt:
        return False
    path.write_text(new, encoding="utf-8")
    return True


def copy_json(dst: pathlib.Path, data: dict) -> bool:
    if not dst.parent.exists():
        dst.parent.mkdir(parents=True, exist_ok=True)
    text = json.dumps(data, ensure_ascii=False, separators=(",", ":"))
    if dst.exists() and dst.read_text(encoding="utf-8") == text:
        return False
    dst.write_text(text, encoding="utf-8")
    return True


def check_consistency(data: dict) -> list:
    issues = []
    compact = publish_compact(data)

    # HTML 锚点 + 内联一致性
    for label, p in [("index.html", H5_INDEX), ("collect.html(res)", BACKEND_RES_HTML)]:
        if not p.exists():
            issues.append(f"{label} 不存在: {p}")
            continue
        txt = p.read_text(encoding="utf-8")
        if "__CITIES__" in txt:
            issues.append(f"{label} 仍含未替换锚点（未同步）: {p}")
        elif f"const CITIES = {compact};" not in txt:
            issues.append(f"{label} 内联数据与发布源不一致: {p}")

    # 后端 resources JSON 一致性
    for label, p in [("resources/cities.json", BACKEND_RES_JSON), ("classes/cities.json", BACKEND_CLS_JSON)]:
        if not p.exists():
            issues.append(f"{label} 不存在: {p}")
            continue
        try:
            j = json.loads(p.read_text(encoding="utf-8"))
        except Exception as e:
            issues.append(f"{label} JSON 损坏: {e}")
            continue
        if j != data:
            issues.append(f"{label} 与发布源不一致: {p}")
    return issues


def main() -> int:
    ap = argparse.ArgumentParser(description="行政区划数据同步器")
    ap.add_argument("--check", action="store_true", help="仅校验一致性，不写文件")
    ap.add_argument("--skip-classes", action="store_true", help="不同步 target/classes")
    args = ap.parse_args()

    data = read_publish()
    compact = publish_compact(data)

    if args.check:
        issues = check_consistency(data)
        if issues:
            print("[一致性校验未通过]")
            for i in issues:
                print(f"  - {i}")
            return 1
        print("[一致性校验通过] 四处落点数据完全一致")
        return 0

    wrote = []
    if inject_html(H5_INDEX, compact):
        wrote.append("index.html")
    if inject_html(BACKEND_RES_HTML, compact):
        wrote.append("collect.html(resources)")

    if copy_json(BACKEND_RES_JSON, data):
        wrote.append("resources/cities.json")
    if not args.skip_classes:
        if copy_json(BACKEND_CLS_JSON, data):
            wrote.append("classes/cities.json")

    if wrote:
        print(f"[同步完成] 更新: {', '.join(wrote)}")
    else:
        print("[同步完成] 四处落点已是最新，无需更新")
    print("提示: 后端 dev 运行如需重新加载 resources，执行 `mvn -o process-resources`")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
