#!/usr/bin/env python3
"""Require native input, authoritative remote outcomes and visible block outlines."""
import base64
import io
import json
import math
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
failure = (r"AssertionError|MixinApplyError|InvalidMixinException|MixinTransformerError|"
           r"Exception in thread|Could not execute entrypoint|This crash report has been saved|"
           r"Failed to handle packet|Network Protocol Error|Error executing task on Client")
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
          "projected_use", "projected_attack", "bucket", "bucket_far", "trade", "mount",
          "sign", "hanging_sign", "reflection")
for phase in phases:
    for name, log in (("CLIENT", client), ("SERVER", server)):
        if not re.search(rf"PORTAL_NATIVE_INTERACTION_{name}_OK phase={phase}\b", log):
            raise AssertionError(f"Missing native {name.lower()} interaction outcome: {phase}")
if "PORTAL_NATIVE_INTERACTION_COMPLETE phases=20" not in client:
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
for phase in ("sign", "hanging_sign"):
    if not re.search(rf"PORTAL_NATIVE_INTERACTION_SIGN_EDITOR_INPUT phase={phase}\b.*"
                     r"lines=2 dimension=minecraft:the_nether", client):
        raise AssertionError(f"The remote {phase} editor did not receive real keyboard input.")
    if not re.search(rf"PORTAL_NATIVE_INTERACTION_SIGN_DESTINATION_TEXT phase={phase}\b.*"
                     r"source=unchanged", server):
        raise AssertionError(f"The native {phase} update did not preserve its source-world sentinel.")

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
frames = {}
for encoded_frame in re.findall(r"PORTAL_NATIVE_OUTLINE_FRAME (\{[^\n]+\})", client):
    frame = json.loads(encoded_frame)
    mode = frame["mode"]
    if mode not in ("on", "off") or mode in frames:
        raise AssertionError("The outline captures did not have unique matching frame metadata.")
    frames[mode] = frame
if set(frames) != {"on", "off"}:
    raise AssertionError("The outline captures lack native scene metadata.")
for frame in frames.values():
    if (frame["stableFrames"] < 8 or frame["screen"] or frame["overlay"]
            or not frame["hudSuppressed"]):
        raise AssertionError("The outline capture included an unsettled scene or user interface.")
for key in ("dimension", "targetDimension", "target", "portal"):
    if frames["on"][key] != frames["off"][key]:
        raise AssertionError(f"The outline comparison changed its native {key}.")
for key in ("camera", "feet", "forward", "bounds", "projection"):
    before, after = frames["on"][key], frames["off"][key]
    if len(before) != len(after) or any(not math.isclose(a, b, abs_tol=1e-6, rel_tol=0)
                                     for a, b in zip(before, after)):
        raise AssertionError(f"The outline comparison changed its native {key}.")
if frames["off"]["frame"] < frames["on"]["frame"] + 8:
    raise AssertionError("The outline toggle did not produce eight completed frames before comparison.")
for key in ("yaw", "pitch"):
    if not math.isclose(frames["on"][key], frames["off"][key], abs_tol=1e-6, rel_tol=0):
        raise AssertionError(f"The outline comparison changed its native {key}.")
bounds = frames["on"]["bounds"]
if not (0 < bounds[0] < bounds[2] < 1 and 0 < bounds[1] < bounds[3] < 1):
    raise AssertionError("The selected block did not project fully into the framebuffer.")
edges = frames["on"]["edges"]
other_edges = frames["off"]["edges"]
if (not edges or len(edges) != len(other_edges)
        or any(len(edge) != 4 or len(other) != 4
               or any(not math.isclose(a, b, abs_tol=1e-6, rel_tol=0)
                      for a, b in zip(edge, other))
               for edge, other in zip(edges, other_edges))):
    raise AssertionError("The selected block's native outline geometry changed between captures.")
pixel_edges = [(a * width, b * height, c * width, d * height) for a, b, c, d in edges]

def near_edge(x, y):
    for ax, ay, bx, by in pixel_edges:
        dx, dy = bx - ax, by - ay
        length_squared = dx * dx + dy * dy
        fraction = 0 if length_squared == 0 else max(0, min(1,
            ((x - ax) * dx + (y - ay) * dy) / length_squared))
        if (x - ax - fraction * dx) ** 2 + (y - ay - fraction * dy) ** 2 <= 16:
            return True
    return False

changed = 0
background_changed = 0
left, top, right, bottom = (bounds[0] * width - 6, bounds[1] * height - 6,
                            bounds[2] * width + 6, bounds[3] * height + 6)
for y in range(height):
    for x in range(width):
        before, after = images["on"].getpixel((x, y)), images["off"].getpixel((x, y))
        difference = max(abs(a - b) for a, b in zip(before, after))
        at_outline = (left <= x <= right and top <= y <= bottom
                      and near_edge(x + .5, y + .5))
        if at_outline:
            # Native high-contrast outlines can be cyan. Require the actual
            # geometry's visible contrast without assuming a particular palette.
            changed += difference >= 25
        elif difference > 12:
            background_changed += 1
metrics = {"outline_contrast_pixels": changed, "background_changed_pixels": background_changed,
           "size": [width, height], "bounds": bounds, "frames": frames}
(evidence / "native-interaction-pixels.json").write_text(json.dumps(metrics, indent=2) + "\n")
if background_changed > width * height * .002:
    raise AssertionError("The outline comparison changed its background scene: " + json.dumps(metrics))
if changed < 30:
    raise AssertionError(f"The native remote outline was not visibly rendered: {metrics}")
print("FABRIC_NATIVE_INTERACTION_VERIFIED " + json.dumps(metrics), flush=True)
