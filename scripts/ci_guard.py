#!/usr/bin/env python3
"""把《06 通用基线》的硬约束变成 CI 可执行的静态门禁。仅用标准库，无第三方依赖。

用法：
  python3 scripts/ci_guard.py --check all
  python3 scripts/ci_guard.py --check version --version 1.0.0
  python3 scripts/ci_guard.py --print-version-code
"""
import argparse
import glob
import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ANDROID_NS = "{http://schemas.android.com/apk/res/android}"

ALLOWED_PERMISSIONS = {
    "android.permission.INTERNET",
    "android.permission.ACCESS_NETWORK_STATE",
    "android.permission.ACCESS_WIFI_STATE",
    "android.permission.CHANGE_WIFI_MULTICAST_STATE",
    "android.permission.WAKE_LOCK",
    "android.permission.MODIFY_AUDIO_SETTINGS",
    "android.permission.POST_NOTIFICATIONS",
    "android.permission.FOREGROUND_SERVICE",
    "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK",
    "android.permission.READ_MEDIA_AUDIO",
    "android.permission.READ_EXTERNAL_STORAGE",
}

# 合并后 Manifest 里由 AndroidX 库自动注入的两项（非危险权限、不弹窗、非主动申请）：
#   WorkManager 2.10 的开机重调度 / androidx.core 在 API<=32 模拟 RECEIVER_NOT_EXPORTED。
# 刻意保留：删除会破坏旧系统上的动态接收器保护与 WorkManager 重启续跑。
# 白名单化是为了让"将来某个依赖偷偷加权限"能立刻在 CI 里失败。
LIBRARY_INJECTED_PERMISSIONS = {
    "android.permission.RECEIVE_BOOT_COMPLETED",
    "com.litelisten.player.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
}

BANNED_DEPENDENCY_PATTERNS = [
    (r"firebase|crashlytics|play-services|admob|umeng|bugly|sentry|analytics|mixpanel|appsflyer",
     "统计 / 广告 / 云 SDK（零遥测红线）"),
    (r"\bcling\b", "cling 已停更且与新版 R8 冲突"),
    (r"\bjcifs\b", "jcifs-ng 依赖已淘汰的 SMB1"),
    (r"com\.google\.android\.gms", "Google Play Services（需联网）"),
]

# 《05 契约》§1 里 Track.displayArtist / displayAlbum 的兜底文案是冻结契约的一部分，
# 但它们位于纯 Kotlin 的 core/model，不能引用 R.string；P1 接入 UI 层时改为资源 id。
CHINESE_LITERAL_EXCEPTIONS = {"core/model/Track.kt"}

failures = []
warnings = []


def fail(msg):
    failures.append(msg)


def check_permissions():
    manifest = ROOT / "app/src/main/AndroidManifest.xml"
    tree = ET.parse(manifest)
    found = {el.get(ANDROID_NS + "name") for el in tree.iter("uses-permission")}
    extra = found - ALLOWED_PERMISSIONS
    missing = ALLOWED_PERMISSIONS - found
    if extra:
        fail("Manifest 出现未授权权限: %s" % ", ".join(sorted(extra)))
    if missing:
        fail("Manifest 缺少既定权限: %s" % ", ".join(sorted(missing)))
    app = tree.find("application")
    if app is None or app.get(ANDROID_NS + "networkSecurityConfig") is None:
        fail("application 未声明 android:networkSecurityConfig")
    return not extra and not missing


def check_dependencies():
    catalog = ROOT / "gradle/libs.versions.toml"
    text = catalog.read_text(encoding="utf-8")
    ok = True
    for lineno, line in enumerate(text.splitlines(), 1):
        code = line.split("#", 1)[0]
        for pattern, reason in BANNED_DEPENDENCY_PATTERNS:
            if re.search(pattern, code, re.IGNORECASE):
                fail("libs.versions.toml:%d 命中禁用依赖 [%s]: %s" % (lineno, reason, code.strip()))
                ok = False
    if "com.hierynomus" not in text or "smbj" not in text:
        fail("未找到 smbj（SMB2/3）依赖")
        ok = False
    return ok


def check_lan_guard():
    interceptor = ROOT / "app/src/main/java/com/litelisten/player/core/network/LanOnlyInterceptor.kt"
    provider = ROOT / "app/src/main/java/com/litelisten/player/core/network/OkHttpProvider.kt"
    if not interceptor.is_file() or "LanAddress.isPrivateHost" not in interceptor.read_text(encoding="utf-8"):
        fail("LanOnlyInterceptor 缺失或未调用 LanAddress.isPrivateHost")
        return False
    if not provider.is_file() or "addInterceptor(lanOnlyInterceptor)" not in provider.read_text(encoding="utf-8"):
        fail("OkHttpProvider 未挂载 LanOnlyInterceptor（存在绕过公网校验的出口）")
        return False
    return True


def check_source_hygiene():
    src = ROOT / "app/src/main/java/com/litelisten/player"
    kt_files = sorted(src.rglob("*.kt"))
    if not kt_files:
        fail("未找到任何 Kotlin 源文件")
        return False
    chinese = re.compile(r'"[^"\n]*[\u4e00-\u9fff][^"\n]*"')
    ok = True
    for path in kt_files:
        raw = path.read_text(encoding="utf-8")
        # 注释里解释约束的文字不算硬编码文案，先剥离再扫。
        text = re.sub(r"/\*.*?\*/", "", raw, flags=re.S)
        text = re.sub(r"//[^\n]*", "", text)
        rel = path.relative_to(src).as_posix()
        for token in ("TODO(", "NotImplementedError(", "GlobalScope", "Thread.sleep", "println(", "Log.d("):
            if token in text:
                fail("%s 含禁止项 %s" % (rel, token))
                ok = False
        for match in chinese.finditer(text):
            if rel in CHINESE_LITERAL_EXCEPTIONS:
                warnings.append("%s 含契约冻结的中文兜底文案（P1 迁移到 strings.xml）" % rel)
            else:
                fail("%s 含硬编码中文: %s" % (rel, match.group(0)[:60]))
                ok = False
    return ok


def check_build_config():
    build = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
    ok = True
    for needle in ('applicationId = "com.litelisten.player"', "minSdk = 26",
                   "targetSdk = 35", "compileSdk = 35", "isMinifyEnabled = true"):
        if needle not in build:
            fail("app/build.gradle.kts 缺少 %s" % needle)
            ok = False
    if "LITELISTEN_REQUIRE_SIGNING" not in build:
        fail("缺少 Release 强制验签逻辑，可能产出 unsigned APK")
        ok = False
    return ok


def read_version_props():
    props = {}
    for line in (ROOT / "version.properties").read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line and not line.startswith("#") and "=" in line:
            key, value = line.split("=", 1)
            props[key.strip()] = value.strip()
    return props


def check_version(expected):
    props = read_version_props()
    name = props.get("versionName")
    code = props.get("versionCode")
    if not re.fullmatch(r"\d+\.\d+\.\d+", name or ""):
        fail("version.properties 的 versionName 不是 x.y.z: %r" % name)
        return False
    if not code or not code.isdigit():
        fail("version.properties 的 versionCode 非法: %r" % code)
        return False
    if expected and expected != name:
        fail("tag 版本 %s 与 version.properties 的 %s 不一致；先运行 python3 scripts/bump_version.py %s"
             % (expected, name, expected))
        return False
    return True


def check_apk_permissions(apk_path):
    """对打包产物做 aapt badging 权限审计：合并后 Manifest 才是最终真相。"""
    if not apk_path:
        warnings.append("未提供 --apk，跳过产物权限审计（release.yml 会执行）")
        return True
    apk = Path(apk_path)
    if not apk.is_file():
        fail("找不到 APK: %s" % apk)
        return False
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    candidates = []
    if sdk:
        candidates = sorted(glob.glob(os.path.join(sdk, "build-tools", "*", "aapt")))
        candidates += sorted(glob.glob(os.path.join(sdk, "build-tools", "*", "aapt.exe")))
    aapt = candidates[-1] if candidates else "aapt"
    try:
        out = subprocess.run([aapt, "dump", "badging", str(apk)],
                             capture_output=True, text=True, timeout=90)
    except (OSError, subprocess.SubprocessError) as exc:
        fail("无法运行 aapt: %s" % exc)
        return False
    if out.returncode != 0:
        fail("aapt dump badging 失败: %s" % out.stderr[:200])
        return False
    found = set(re.findall(r"uses-permission: name='([^']+)'", out.stdout))
    unexpected = found - ALLOWED_PERMISSIONS - LIBRARY_INJECTED_PERMISSIONS
    if unexpected:
        fail("APK 出现白名单外权限: %s" % ", ".join(sorted(unexpected)))
    injected = found & LIBRARY_INJECTED_PERMISSIONS
    for name in sorted(injected):
        warnings.append("库注入权限（已审计保留）: %s" % name)
    return not unexpected


def check_no_committed_secrets():
    try:
        tracked = subprocess.run(
            ["git", "-C", str(ROOT), "ls-files"], capture_output=True, text=True, timeout=10
        )
    except (OSError, subprocess.SubprocessError):
        warnings.append("未检测到 git 仓库，跳过已提交密钥检查（CI 环境会自动执行）")
        return True
    if tracked.returncode != 0:
        warnings.append("git ls-files 不可用，跳过已提交密钥检查")
        return True
    ok = True
    for path in tracked.stdout.splitlines():
        lower = path.lower()
        if lower.endswith((".jks", ".keystore", ".pem", ".pk8", ".pepk")) or lower.endswith("keystore.properties"):
            fail("签名资产被提交进仓库: %s" % path)
            ok = False
    return ok


CHECKS = {
    "permissions": check_permissions,
    "dependencies": check_dependencies,
    "lan": check_lan_guard,
    "source": check_source_hygiene,
    "build": check_build_config,
    "secrets": check_no_committed_secrets,
}


def main():
    parser = argparse.ArgumentParser(description="轻听 CI 红线守卫")
    parser.add_argument("--check", default="all", choices=["all", *CHECKS, "version", "apk"])
    parser.add_argument("--version", default="", help="期望的版本号（tag 去掉 v）")
    parser.add_argument("--apk", default="", help="待审计权限的 APK 路径")
    parser.add_argument("--print-version-code", action="store_true")
    args = parser.parse_args()

    if args.print_version_code:
        print(read_version_props().get("versionCode", "1"))
        return 0

    if args.check == "version":
        ok = check_version(args.version)
    elif args.check == "apk":
        ok = check_apk_permissions(args.apk)
    elif args.check == "all":
        ok = check_version(args.version) and all(fn() for fn in CHECKS.values())
        if args.apk:
            ok = check_apk_permissions(args.apk) and ok
    else:
        ok = CHECKS[args.check]()

    for msg in warnings:
        print("WARN  " + msg)
    if ok and not failures:
        print("PASS  红线检查通过：权限 / 依赖 / 私网闸口 / 源码 / 构建 / 版本 / 密钥")
        return 0
    for msg in failures:
        print("FAIL  " + msg, file=sys.stderr)
    print("红线检查失败（%d 项）" % len(failures), file=sys.stderr)
    return 1


if __name__ == "__main__":
    sys.exit(main())
