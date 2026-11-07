#!/usr/bin/env python3
"""Compare real pictures to physical ray/box geometry and native-world lighting references."""
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

backend, evidence_path = sys.argv[1:3]
evidence = Path(evidence_path)
client = (evidence / "client-console.log").read_text(errors="replace")
server = (evidence / "server-console.log").read_text(errors="replace")
actual = "Vulkan" if backend == "vulkan" else "OpenGL"
if not re.search(rf"Using graphics backend {actual}\b", client):
    raise AssertionError(f"The optical probe did not use the requested {actual} backend.")
for name, log in (("client", client), ("server", server)):
    if re.search(r"AssertionError|MixinApplyError|InvalidMixinException|MixinTransformerError|"
                 r"Could not execute entrypoint|Exception in thread|This crash report has been saved", log):
        raise AssertionError(f"The optical {name} reported a runtime failure.")
if "PORTAL_OPTICS_FIXTURE_READY faces=4" not in server or "PORTAL_OPTICS_COMPLETE views=41" not in client:
    raise AssertionError("The physical optical fixture and all 41 camera views did not complete.")
modset = os.environ.get("PORTAL_CI_MODSET", "vanilla")
if modset in ("sodium", "iris") and "PORTAL_OPTICS_SODIUM_OK" not in client:
    raise AssertionError("The optical probe did not initialize its installed Sodium adapter.")
if modset == "iris" and "PORTAL_OPTICS_IRIS_OK" not in client:
    raise AssertionError("The optical probe did not enable the real-lightmap shaderpack.")

expected = [f"sweep-{d}-{pitch}-{yaw}" for d in range(4)
            for pitch in (30, 45, 60, 89) for yaw in (0, 1)]
expected += [f"occlusion-{mode}" for mode in ("source", "destination", "restored")]
expected += [f"{state}-{view}" for state in ("dark", "lit", "dark_again")
             for view in ("portal", "reference")]
records = {}
for match in re.finditer(r"PORTAL_OPTICS_FRAME (\{[^\n]+\})", client):
    record = json.loads(match.group(1))
    records.setdefault(record["name"], []).append(record)
if set(records) != set(expected):
    raise AssertionError(f"The optical views differ: expected={expected}, received={list(records)}")


def add(first, second, scale=1):
    return tuple(a + b * scale for a, b in zip(first, second))


def box_hit(origin, direction, bounds):
    lower, upper = -math.inf, math.inf
    for axis in range(3):
        if abs(direction[axis]) < 1e-12:
            if not bounds[axis] <= origin[axis] <= bounds[axis + 3]:
                return None
            continue
        first = (bounds[axis] - origin[axis]) / direction[axis]
        second = (bounds[axis + 3] - origin[axis]) / direction[axis]
        lower = max(lower, min(first, second))
        upper = min(upper, max(first, second))
    return max(lower, 0) if upper >= max(lower, 0) and upper > 1e-7 else None


def nearest(origin, direction, boxes):
    hits = [(hit, color) for bounds, color in boxes
            if (hit := box_hit(origin, direction, bounds)) is not None]
    return min(hits) if hits else (math.inf, None)


source = [((116, 125, 116, 141, 126, 141), "gray"),
          ((116, 118, 127, 141, 143, 128), "gray")]
destination = [((116, 125, 116, 141, 126, 141), "green"),
               ((116, 126, 116, 117, 135, 141), "green"),
               ((140, 126, 116, 141, 135, 141), "green"),
               ((116, 126, 116, 141, 135, 117), "green"),
               ((116, 126, 140, 141, 135, 141), "green"),
               ((116, 134, 116, 141, 135, 141), "green")]


def expected_color(record, ray):
    origin = record["camera"]
    boxes = source + ([((124, 126, 129, 133, 134, 130), "gray")]
                      if record["mode"] == "source" else [])
    first, color = nearest(origin, ray, boxes)
    if ray[2] >= -1e-9 or origin[2] <= 128:
        return color
    plane = (128 - origin[2]) / ray[2]
    point = add(origin, ray, plane)
    # Exclude silhouette pixels and the exact floor/aperture tie. Those are sensitive
    # to antialiasing, while interior samples have an unambiguous nearest world face.
    if min(abs(point[0] - 124), abs(point[0] - 132),
           abs(point[1] - 126), abs(point[1] - 134)) < .12:
        return None
    if plane <= first + 1e-5 and 124 < point[0] < 132 and 126 < point[1] < 134:
        boxes = destination + ([((124, 126, 126, 133, 134, 127), "gray")]
                               if record["mode"] == "destination" else [])
        return nearest(add(point, ray, 1e-6), ray, boxes)[1]
    return color


def pixel_color(pixel, expected_color):
    r, g, b = pixel
    if expected_color == "green":
        return g > r * 1.12 + 6 and g > b * 1.03 + 3 and g >= 20
    return max(pixel) - min(pixel) < 30 and max(pixel) > 8


def thumbnail(rgb, name):
    copy = Image.new("RGB", rgb.size)
    copy.paste(rgb)
    copy.thumbnail((512, 288), Image.Resampling.LANCZOS)
    while True:
        data = io.BytesIO()
        copy.save(data, format="PNG", optimize=True)
        if data.tell() < 28 * 1024:
            break
        copy = copy.resize((max(1, copy.width * 3 // 4), max(1, copy.height * 3 // 4)))
    print(f"PORTAL_OPTICS_THUMBNAIL name={name} data="
          + base64.b64encode(data.getvalue()).decode("ascii"), flush=True)


metrics = {}
lighting = {}
failures = []
screenshots = Path("run/smoke-client/screenshots")
for name in expected:
    frames = records[name]
    if [record["sample"] for record in frames] != list(range(12)):
        raise AssertionError(f"The {name} optical probe did not capture all twelve frames.")
    if any(b["frame"] != a["frame"] + 1 for a, b in zip(frames, frames[1:])):
        raise AssertionError(f"The {name} optical probe skipped completed GPU frames.")
    values = []
    for record in frames:
        path = screenshots / f"portal-optics-{name}-{record['sample']:02}.png"
        if not path.is_file():
            raise AssertionError(f"Missing physical optical evidence: {path}")
        with Image.open(path) as capture:
            rgb = capture.convert("RGB")
        if record["sample"] == 0 and (name in ("sweep-0-89-0", "sweep-2-45-1",
                "occlusion-source", "occlusion-destination", "dark-portal", "dark-reference",
                "lit-portal", "lit-reference")):
            thumbnail(rgb, name)
        if name.startswith(("dark", "lit")):
            patch = rgb.crop((int(.42 * rgb.width), int(.42 * rgb.height),
                              int(.58 * rgb.width), int(.58 * rgb.height)))
            luminance = sum(.2126 * r + .7152 * g + .0722 * b
                            for r, g, b in patch.getdata()) / (patch.width * patch.height)
            values.append({"frame": record["frame"], "mean_luminance": luminance})
            continue
        counts = {"green": [0, 0], "gray": [0, 0]}
        for y in range(int(.2 * rgb.height), int(.8 * rgb.height), 12):
            for x in range(int(.15 * rgb.width), int(.85 * rgb.width), 12):
                sx, sy = 2 * (x + .5) / rgb.width - 1, 1 - 2 * (y + .5) / rgb.height
                ray = add(add(record["forward"], record["left"],
                              -sx / record["projectionScale"][0]), record["up"],
                          sy / record["projectionScale"][1])
                color = expected_color(record, ray)
                if color is None:
                    continue
                patch = rgb.crop((x - 1, y - 1, x + 2, y + 2))
                pixels = list(patch.getdata())
                pixel = tuple(sum(p[c] for p in pixels) / len(pixels) for c in range(3))
                counts[color][1] += 1
                counts[color][0] += pixel_color(pixel, color)
        ratios = {color: matched / total for color, (matched, total) in counts.items() if total}
        if sum(total for _, total in counts.values()) < 100:
            failures.append(f"The {name} geometry oracle observed too little terrain: {counts}")
        for color, (matched, total) in counts.items():
            if total >= 20 and matched / total < .85:
                failures.append(f"The {name} frame {record['sample']} exposed the wrong world/face: "
                                f"expected={color}, matched={matched}/{total}")
        values.append({"frame": record["frame"], "terrain_counts": counts, "match_ratios": ratios})
    metrics[name] = values
    if name.startswith(("dark", "lit")):
        lighting[name] = sum(value["mean_luminance"] for value in values) / len(values)

for state in ("dark", "lit", "dark_again"):
    portal, reference = lighting[state + "-portal"], lighting[state + "-reference"]
    if abs(portal - reference) > 5 + reference * .25:
        failures.append(f"{state} portal light differs from the native destination: "
                        f"portal={portal}, reference={reference}")
for view in ("portal", "reference"):
    dark, lit = lighting["dark-" + view], lighting["lit-" + view]
    dark_again = lighting["dark_again-" + view]
    if dark >= lit * .65 or lit - dark < 10:
        failures.append(f"The sealed {view} room did not respond to real block lighting: "
                        f"dark={dark}, lit={lit}")
    if abs(dark_again - dark) > 5 + dark * .25:
        failures.append(f"The {view} room stayed bright after its lamps were removed: "
                        f"dark={dark}, dark_again={dark_again}")

result = {"views": metrics, "lighting": lighting}
(evidence / "optics-pixel-metrics.json").write_text(json.dumps(result, indent=2) + "\n")
print("PORTAL_OPTICS_PIXEL_METRICS " + json.dumps(result, sort_keys=True), flush=True)
if failures:
    raise AssertionError("\n".join(failures))
print("PORTAL_OPTICS_GEOMETRY_OK views=35 consecutive_frames=12", flush=True)
print("PORTAL_OPTICS_LIGHTING_OK " + json.dumps(lighting, sort_keys=True), flush=True)
print(f"FABRIC_OPTICS_VERIFIED backend={backend} views=41 frames=492", flush=True)
