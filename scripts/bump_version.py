#!/usr/bin/env python3
"""发版前更新 version.properties：versionName 设为指定版本，versionCode 自动 +1。

用法：python3 scripts/bump_version.py 1.0.1
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FILE = ROOT / "version.properties"


def main():
    if len(sys.argv) != 2 or not re.fullmatch(r"\d+\.\d+\.\d+", sys.argv[1]):
        print("用法: python3 scripts/bump_version.py <x.y.z>", file=sys.stderr)
        return 2
    new_name = sys.argv[1]
    lines = FILE.read_text(encoding="utf-8").splitlines()
    old_code = 0
    for line in lines:
        if line.strip().startswith("versionCode="):
            old_code = int(line.split("=", 1)[1].strip())
    out = []
    for line in lines:
        if line.strip().startswith("versionCode="):
            out.append("versionCode=%d" % (old_code + 1))
        elif line.strip().startswith("versionName="):
            out.append("versionName=%s" % new_name)
        else:
            out.append(line)
    FILE.write_text("\n".join(out) + "\n", encoding="utf-8", newline="\n")
    print("versionCode: %d -> %d, versionName: %s" % (old_code, old_code + 1, new_name))
    return 0


if __name__ == "__main__":
    sys.exit(main())
