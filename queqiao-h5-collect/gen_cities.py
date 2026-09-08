#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
gen_cities.py —— 行政区划发布数据生成器（数据维护脚本）

职责：
  1. 从 source/admin-divisions.json（可编辑的权威源）读取省→市→[区/县] 数据；
  2. 做完整性自检（省份数、地级市数、不设区市、省内区县重复检测）；
  3. 生成发布用 cities.json（压缩、无空白、确保_ascii=False 中文可读）。

数据源约定（source/admin-divisions.json）：
  {
    "省份名": {
      "地级市名": ["区/县名", ...],   // 不设区市其值为 []
      ...
    },
    ...
  }
  - 直辖市：省份=市，如 "北京": {"北京": [...]}
  - 不设区市（东莞/中山/嘉峪关/儋州/三沙等）区县列表为空 []
  - 港澳台按实际区划录入

用法：
  python gen_cities.py            # 校验并打印统计，生成 ../cities.json
  python gen_cities.py --check    # 仅校验，不写文件

依赖：Python 3.8+，仅标准库（json / pathlib / argparse）。
"""
import argparse
import json
import pathlib
import sys

HERE = pathlib.Path(__file__).resolve().parent
SOURCE = HERE / "source" / "admin-divisions.json"
OUT = HERE / "cities.json"

# 期望的最低完整性阈值（用于 CI / 维护时快速发现数据退化）
EXPECT_PROVINCES = 34          # 23 省 + 5 自治区 + 4 直辖市 + 2 特别行政区(港/澳) + 台湾
EXPECT_MIN_CITIES = 340        # 地级市 + 地区 + 盟 + 自治州 大致下限
EXPECT_MIN_DISTRICTS = 2800    # 县级行政区大致下限


def load_source() -> dict:
    if not SOURCE.exists():
        print(f"[错误] 权威源文件不存在: {SOURCE}", file=sys.stderr)
        sys.exit(1)
    try:
        data = json.loads(SOURCE.read_text(encoding="utf-8"))
    except json.JSONDecodeError as e:
        print(f"[错误] 权威源 JSON 解析失败: {e}", file=sys.stderr)
        sys.exit(1)
    if not isinstance(data, dict) or not data:
        print("[错误] 权威源结构应为 {省份: {城市: [区县...]}}", file=sys.stderr)
        sys.exit(1)
    return data


def validate(data: dict) -> list:
    """返回问题列表（空列表表示通过）。"""
    issues = []

    provinces = list(data.keys())
    if len(provinces) < EXPECT_PROVINCES:
        issues.append(f"省份数 {len(provinces)} 低于期望 {EXPECT_PROVINCES}")

    city_count = 0
    district_count = 0
    no_district = []
    dup = {}

    for prov, cities in data.items():
        if not isinstance(cities, dict):
            issues.append(f"省份 {prov} 的子结构应为 dict")
            continue
        for city, dists in cities.items():
            city_count += 1
            if not isinstance(dists, list):
                issues.append(f"{prov}/{city} 的区县应为 list")
                continue
            if len(dists) == 0:
                no_district.append(f"{prov}/{city}")
            district_count += len(dists)
            # 省内区县重名检测（同名不同市属合法，但需关注）
            seen = {}
            for d in dists:
                if d in seen:
                    dup.setdefault(prov, []).append(f"{d}({seen[d]}/{city})")
                else:
                    seen[d] = city

    if city_count < EXPECT_MIN_CITIES:
        issues.append(f"地级市数 {city_count} 低于期望 {EXPECT_MIN_CITIES}")
    if district_count < EXPECT_MIN_DISTRICTS:
        issues.append(f"区县数 {district_count} 低于期望 {EXPECT_MIN_DISTRICTS}")

    print(f"省份: {len(provinces)}, 地级市: {city_count}, 区县: {district_count}")
    print(f"不设区市(区县为空): {len(no_district)} 个 -> {', '.join(no_district) if no_district else '无'}")
    if dup:
        print(f"[提示] 省内区县重名(同名不同市属，需人工确认): {dup}")
    else:
        print("省内区县重名: 无")

    return issues


def generate(data: dict) -> None:
    OUT.write_text(
        json.dumps(data, ensure_ascii=False, separators=(",", ":")),
        encoding="utf-8",
    )
    print(f"已生成发布数据: {OUT}  ({(OUT.stat().st_size)} 字节)")


def main() -> int:
    ap = argparse.ArgumentParser(description="行政区划发布数据生成器")
    ap.add_argument("--check", action="store_true", help="仅校验，不写文件")
    args = ap.parse_args()

    data = load_source()
    issues = validate(data)
    if issues:
        print("\n[校验未通过]")
        for i in issues:
            print(f"  - {i}")
        if not args.check:
            print("（仍会生成文件，但请尽快修正上述问题）")
    else:
        print("\n[校验通过] 数据完整性满足期望阈值")

    if args.check:
        return 1 if issues else 0

    generate(data)
    return 1 if issues else 0


if __name__ == "__main__":
    raise SystemExit(main())
