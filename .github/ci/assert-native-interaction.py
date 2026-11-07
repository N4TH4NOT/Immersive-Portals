#!/usr/bin/env python3
"""Require native input, authoritative remote outcomes and visible block outlines."""
import base64
import io
import json
import os
from pathlib import Path
import re
import sys

if os.environ.get("GITHUB_ACTIONS") != "true":
    raise SystemExit("Automated validation is restricted to GitHub Actions.")

from PIL import Image

backend, directory = sys.argv[1:3]
evidence = Path(directory)
client = (evidence / "client-console.log").read_text(errors="replace")
server = (evidence / "server-console.log").read_text(errors="replace")
failure = r"AssertionError|MixinApplyError|InvalidMixinException|MixinTransformerError|Exception in thread|Could not execute entrypoint|This crash report has been saved"
for name, log in (("client", client), ("server", server)):
    if re.search(failure, log):
        raise AssertionError(f"The native interaction {name} reported a failure.")
actual = "Vulkan" if backend == "vulkan" else "OpenGL"
if not re.search(rf"Using graphics backend {actual}\b", client):
    raise AssertionError("The native interaction scenario used the wrong graphics backend.")
if os.environ.get("PORTAL_CI_MODSET") == "sodium" and "PORTAL_NATIVE_INTERACTION_SODIUM_COMPAT_OK" not in client:
    raise AssertionError("The native interaction scenario did not exercise its installed Sodium adapter.")
phases = ("chest", "button", "place", "break", "entity_use", "entity_attack",
          "source_occluder", "destination_occluder", "outside", "out_of_reach", "mining_cross",
          "projected_use", "projected_attack", "bucket", "bucket_far", "trade", "mount", "reflection")
for phase in phases:
    for name, log in (("CLIENT", client), ("SERVER", server)):
        if not re.search(rf"PORTAL_NATIVE_INTERACTION_{name}_OK phase={phase}\b", log):
            raise AssertionError(f"Missing native {name.lower()} interaction outcome: {phase}")
if "PORTAL_NATIVE_INTERACTION_COMPLETE phases=18" not in client:
    raise AssertionError("Native interaction input did not complete all phases.")
if "PORTAL_NATIVE_INTERACTION_FIXTURE_READY faces=4 translation=64" not in server:
    raise AssertionError("Native interactions did not use the real four-face wand fixture.")
outline = re.search(r"PORTAL_NATIVE_INTERACTION_OUTLINE_OK remoteFrames=(\d+) primaryChecks=(\d+)", client)
if not outline or min(map(int, outline.groups())) < 2:
    raise AssertionError("Remote outline extraction and primary-view isolation were not observed.")
if "PORTAL_NATIVE_INTERACTION_MINING_CROSS" not in client:
    raise AssertionError("Mining did not continue during a real portal crossing.")
for phase in ("projected_use", "projected_attack"):
    if f"PORTAL_NATIVE_INTERACTION_PROJECTED_BODY phase={phase}" not in client:
        raise AssertionError(f"The interaction did not target an actually projected entity body: {phase}")
trade = re.search(r"PORTAL_NATIVE_INTERACTION_TRADE_STABLE ticks=(\d+)", client)
if not trade or int(trade.group(1)) < 25:
    raise AssertionError("The remote merchant menu did not remain valid through native ticks.")
for marker in ("TRADE_CLOSED_ON_OBSTRUCTION", "MOUNT_SAME_WORLD", "REFLECTION_SOURCE_WORLD drops=7",
               "BUCKET_SOURCE_COLLECTED", "BUCKET_NORMAL_REACH range=4.5"):
    if f"PORTAL_NATIVE_INTERACTION_{marker}" not in server:
        raise AssertionError(f"Missing native entity action evidence: {marker}")
if "PORTAL_NATIVE_INTERACTION_MINING_SERVER_COMPLETE" not in server:
    raise AssertionError("Held mining did not finish on the authoritative server.")

screenshots = Path("run/smoke-client/screenshots")
images = {}
for mode in ("on", "off"):
    path = screenshots / f"portal-native-outline-{mode}.png"
    if not path.is_file():
        raise AssertionError(f"Missing native outline pixel evidence: {path}")
    with Image.open(path) as source:
        images[mode] = source.convert("RGB")
    thumbnail = images[mode].copy()
    thumbnail.thumbnail((320, 180))
    encoded = io.BytesIO()
    thumbnail.save(encoded, format="PNG")
    print(f"PORTAL_NATIVE_OUTLINE_THUMBNAIL name={mode} data="
          + base64.b64encode(encoded.getvalue()).decode("ascii"), flush=True)
if images["on"].size != images["off"].size:
    raise AssertionError("The outline comparison changed framebuffer dimensions.")
width, height = images["on"].size
changed = 0
for y in range(int(.25 * height), int(.80 * height)):
    for x in range(int(.30 * width), int(.70 * width)):
        before, after = images["on"].getpixel((x, y)), images["off"].getpixel((x, y))
        changed += min(before) >= 210 and min(before) - min(after) >= 25
metrics = {"white_outline_pixels": changed, "size": [width, height]}
(evidence / "native-interaction-pixels.json").write_text(json.dumps(metrics, indent=2) + "\n")
if changed < 30:
    raise AssertionError(f"The native remote outline was not visibly rendered: {metrics}")
print("FABRIC_NATIVE_INTERACTION_VERIFIED " + json.dumps(metrics), flush=True)
