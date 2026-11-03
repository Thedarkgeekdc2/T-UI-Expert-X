#!/usr/bin/env python3
"""Static validation for T-UI Expert X Phase 1 source changes."""
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]

def require(condition, message):
    if not condition:
        raise SystemExit("FAIL: " + message)
    print("PASS: " + message)

xml_files = list((ROOT / "app/src/main").rglob("*.xml"))
for xml_file in xml_files:
    ET.parse(xml_file)
print("PASS: parsed", len(xml_files), "Android XML files")

resources_root = ET.parse(ROOT / "app/src/main/res/values/strings.xml").getroot()
strings = {item.get("name"): item.text or "" for item in resources_root.findall("string")}
tutorial = resources_root.find('string-array[@name="tutorial"]')
tutorial_text = "\n".join(item.text or "" for item in tutorial.findall("item")) if tutorial is not None else ""
java = (ROOT / "app/src/main/java/de/reckendrees/systems/tui/expert/commands/main/raw/cyber.java").read_text()
build = (ROOT / "app/build.gradle").read_text()
manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text()
proguard = (ROOT / "app/proguard-rules.pro").read_text()

require("applicationId \"com.thedarkgeekdc.tuiexpert\"" in build, "branded application ID")
require('versionName "0.5.8"' in build and "versionCode 17" in build, "current app version")
require("help_cyber" in strings and "-trace" in strings["help_cyber"], "Cyber Toolbox help resource")
require("cyber -help" in tutorial_text, "first-run tutorial mentions Cyber Toolbox")
require(all(re.search(r"^\s*" + name + r"\s*\{", java, re.M) for name in ("ip", "net", "dns", "ping", "trace")), "five diagnostics subcommands")
require("executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)" in java, "network diagnostics run asynchronously")
require('"-c", "4"' in java and '"-W", "2"' in java, "ping is limited to four probes")
require('"-m", "8"' in java, "traceroute is capped at eight hops")
require('host.matches("[A-Za-z0-9._:%-]+")' in java, "host input is restricted")
require("Runtime.getRuntime().exec" not in java and "sh -c" not in java, "no shell command interpolation")
require("android.permission.INTERNET" in manifest and "android.permission.ACCESS_NETWORK_STATE" in manifest, "required network permissions already exist")
require("commands.main.raw.*" in proguard, "dynamic commands retained by ProGuard")
require(all(name in strings.get("output_about", "") for name in ("TheDarkGeekDC", "Francesco Andreuzzi", "v1nc", "Yehoslav")), "maintainer and upstream credits retained")
print("All Phase 1 static checks passed.")
