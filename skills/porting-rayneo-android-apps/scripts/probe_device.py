#!/usr/bin/env python3
"""Read-only RayNeo/Android capability snapshot, using only Python's stdlib."""

import argparse
from datetime import datetime, timezone
import json
import math
from pathlib import Path
import re
import subprocess
import sys


MAX_CHARS = 8192
MAX_LINES = 80
DISPLAY_LINES = re.compile(
    r"DisplayDeviceInfo|DisplayInfo|mDisplayId|mLogicalWidth|mLogicalHeight|"
    r"mCurrentDisplayRect|mDisplayRect|mDefaultViewport|mInternalViewport"
)
INPUT_LINES = re.compile(
    r"^\s*(?:Device \d|Name:|Sources:|KeyboardType:|Keyboard Type:|"
    r"Motion Ranges:|X:|Y:|AXIS_X:|AXIS_Y:)|^\s*\d+:.*(?:cyttsp|capsense)", re.I
)
PACKAGE_LINES = re.compile(
    r"^\s*(?:versionCode=|versionName=|minSdk=|targetSdk=|User \d+:|"
    r"requested permissions:|install permissions:|runtime permissions:|"
    r"android\.permission\.(?:MANAGE_EXTERNAL_STORAGE|READ_EXTERNAL_STORAGE|"
    r"WRITE_EXTERNAL_STORAGE|READ_MEDIA_[A-Z_]+)|com\.rayneo\.mercury\.app)"
)


def serial_identifier(value):
    # ADB forwards shell arguments to the device shell; validate identifiers even
    # though the host subprocess never uses a shell. Supports USB, Wi-Fi and IPv6.
    if not re.fullmatch(r"(?!-)[A-Za-z0-9_.:\[\]-]{1,256}", value):
        raise argparse.ArgumentTypeError("serial must contain only letters, digits, . _ : [ ] - and must not start with -")
    return value


def package_identifier(value):
    if not re.fullmatch(r"[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)+", value):
        raise argparse.ArgumentTypeError("package must be a dotted Android package name, e.g. com.example.app")
    return value


def timeout_seconds(value):
    try:
        seconds = float(value)
    except ValueError as exc:
        raise argparse.ArgumentTypeError("timeout must be a number") from exc
    if not math.isfinite(seconds) or not 0 < seconds <= 30:
        raise argparse.ArgumentTypeError("timeout must be greater than 0 and at most 30 seconds")
    return seconds


def parse_args(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True, type=serial_identifier,
                        help="exact serial from adb devices -l; never selects a device implicitly")
    parser.add_argument("--package", type=package_identifier,
                        help="optional package for version, storage permissions and appop queries")
    parser.add_argument("--adb", default="adb", help="adb executable path (default: adb on PATH)")
    parser.add_argument("--timeout", type=timeout_seconds, default=8.0,
                        help="per-command timeout in seconds, at most 30 (default: 8)")
    parser.add_argument("--output", type=Path,
                        help="write UTF-8 JSON to a new local file; existing files are preserved")
    return parser.parse_args(argv)


def summarize(raw, pattern=None):
    lines = raw.splitlines()
    if pattern is not None:
        lines = [line for line in lines if pattern.search(line)]
    text = "\n".join(lines[:MAX_LINES]).strip()
    truncated = len(lines) > MAX_LINES or len(text) > MAX_CHARS
    return text[:MAX_CHARS], truncated


def query(options, arguments, pattern=None):
    argv = [options.adb, "-s", options.serial, *arguments]
    entry = {"command": arguments, "status": "ok", "returncode": None,
             "stdout": "", "stderr": "", "truncated": False}
    try:
        result = subprocess.run(argv, shell=False, capture_output=True, text=True,
                                encoding="utf-8", errors="replace", timeout=options.timeout)
    except subprocess.TimeoutExpired:
        entry.update(status="timeout", stderr=f"Command exceeded {options.timeout:g} seconds")
        return entry
    except OSError as exc:
        entry.update(status="error", stderr=str(exc)[:MAX_CHARS])
        return entry
    entry["returncode"] = result.returncode
    entry["status"] = "ok" if result.returncode == 0 else "error"
    entry["stdout"], out_truncated = summarize(result.stdout, pattern)
    entry["stderr"], err_truncated = summarize(result.stderr)
    entry["truncated"] = out_truncated or err_truncated
    return entry


def collect_report(options):
    results = {}
    report = {"schema_version": 1, "captured_at_utc": datetime.now(timezone.utc).isoformat(),
              "serial": options.serial, "package": options.package, "android_user": None,
              "package_installed_for_user": None,
              "complete": False, "queries": results}

    def add(name, arguments, pattern=None):
        results[name] = query(options, arguments, pattern)
        return results[name]

    state = add("transport", ["get-state"])
    if state["status"] != "ok":
        return report
    if state["stdout"] != "device":
        state.update(status="invalid_output", stderr="Selected transport is not in device state")
        return report

    for short_name, prop in [
        ("manufacturer", "ro.product.manufacturer"), ("model", "ro.product.model"),
        ("device", "ro.product.device"), ("brand", "ro.product.brand"),
        ("android_release", "ro.build.version.release"), ("android_sdk", "ro.build.version.sdk"),
    ]:
        add(short_name, ["shell", "getprop", prop])
    add("wm_size", ["shell", "wm", "size"])
    add("wm_density", ["shell", "wm", "density"])
    add("display_summary", ["shell", "dumpsys", "display"], DISPLAY_LINES)
    add("input_summary", ["shell", "dumpsys", "input"], INPUT_LINES)
    current_user = add("current_user", ["shell", "am", "get-current-user"])
    user_id = current_user["stdout"]
    if current_user["status"] == "ok" and not re.fullmatch(r"[0-9]{1,10}", user_id):
        current_user.update(status="invalid_output", stderr="Current Android user was not a numeric ID")

    user_queries = [
        ("document_tree_picker", ["shell", "cmd", "package", "resolve-activity", "--brief",
          "--user", user_id, "-a", "android.intent.action.OPEN_DOCUMENT_TREE"]),
        ("all_files_settings", ["shell", "cmd", "package", "resolve-activity", "--brief",
          "--user", user_id, "-a", "android.settings.MANAGE_ALL_FILES_ACCESS_PERMISSION"]),
    ]
    if options.package:
        add("package_summary", ["shell", "dumpsys", "package", options.package], PACKAGE_LINES)
        user_queries.extend([
            ("package_presence", ["shell", "cmd", "package", "list", "packages",
              "--user", user_id, options.package]),
            ("app_all_files_settings", ["shell", "cmd", "package", "resolve-activity", "--brief",
              "--user", user_id, "-a", "android.settings.MANAGE_APP_ALL_FILES_ACCESS_PERMISSION",
              "-d", f"package:{options.package}"]),
            ("all_files_appop", ["shell", "cmd", "appops", "get", "--user", user_id,
              options.package, "MANAGE_EXTERNAL_STORAGE"]),
        ])
    if current_user["status"] == "ok":
        report["android_user"] = int(user_id)
        for name, arguments in user_queries:
            result = add(name, arguments)
            if name == "package_presence" and result["status"] == "ok":
                report["package_installed_for_user"] = (
                    f"package:{options.package}" in result["stdout"].splitlines()
                )
    else:
        for name, _ in user_queries:
            results[name] = {"status": "skipped", "reason": "Current Android user is unknown"}

    report["complete"] = all(result["status"] == "ok" for result in results.values())
    return report


def main(argv=None):
    options = parse_args(argv)
    if options.output is not None and options.output.exists():
        print(f"Output already exists: {options.output}", file=sys.stderr)
        return 2
    report = collect_report(options)
    payload = json.dumps(report, ensure_ascii=False, indent=2) + "\n"
    if options.output is None:
        print(payload, end="")
    else:
        try:
            with options.output.open("x", encoding="utf-8", newline="\n") as destination:
                destination.write(payload)
        except OSError as exc:
            print(f"Writing report failed: {exc}", file=sys.stderr)
            return 2
        print(f"Report saved: {options.output}")
    return 0 if report["complete"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
