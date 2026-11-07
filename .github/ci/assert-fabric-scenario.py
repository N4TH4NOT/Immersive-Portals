#!/usr/bin/env python3
"""Require game assertions and the actual backend; a clean launch alone is insufficient."""
import os
import json
from pathlib import Path
import re
import sys

if os.environ.get("GITHUB_ACTIONS") != "true":
    raise SystemExit("Automated validation is restricted to GitHub Actions.")

backend, scenario, evidence_path = sys.argv[1:4]
reloaded = sys.argv[4:] == ["--reload"]
modset = os.environ.get("PORTAL_CI_MODSET", "vanilla")
evidence = Path(evidence_path)
prefix = "reload-" if reloaded else ""
client = (evidence / f"{prefix}client-console.log").read_text(errors="replace")
server = (
    client
    if scenario == "singleplayer"
    else (evidence / f"{prefix}server-console.log").read_text(errors="replace")
)


def require(pattern: str, text: str, description: str) -> None:
    if not re.search(pattern, text, re.IGNORECASE):
        raise AssertionError(description)


actual_backend = "Vulkan" if backend == "vulkan" else "OpenGL"
require(
    rf"Using graphics backend {actual_backend}\b",
    client,
    f"Minecraft did not initialize the requested {actual_backend} backend; fallback is a failure.",
)
for stream_name, stream in (("client", client), ("server", server)):
    if re.search(
        r"AssertionError|MixinApplyError|InvalidMixinException|MixinTransformerError|"
        r"Could not execute entrypoint|Exception in thread|This crash report has been saved",
        stream,
    ):
        raise AssertionError(f"Runtime failure reported by the {stream_name}; inspect its logs.")

client_markers = [
    "PORTAL_SMOKE_ONBOARDING_OK",
    "PORTAL_SMOKE_ITEM_MODELS_OK",
    "PORTAL_SMOKE_PERFORMANCE_OK",
    "PORTAL_SMOKE_MIGRATION_RESOURCES_OK",
]
server_markers = [
    "PORTAL_SMOKE_ALTERNATE_DIMENSIONS_OK",
    "PORTAL_SMOKE_SKYLAND_WORLD_SEED_OK",
    "PORTAL_SMOKE_SERVER_READY",
]
if scenario in ("multiplayer", "singleplayer"):
    client_markers += [
        "PORTAL_SMOKE_WALL_PORTAL_OK",
        "PORTAL_SMOKE_SEAMLESS_MOVEMENT_OK",
        "PORTAL_SMOKE_SEAMLESS_SAME_WORLD_OK",
    ]
    server_markers += [
        "PORTAL_SMOKE_COLLISION_PHYSICS_OK",
        "PORTAL_SMOKE_WALL_PORTAL_READY",
        "PORTAL_SMOKE_WAND_CLUSTER_READY",
        "PORTAL_SMOKE_SERVER_WALL_PORTAL_OK",
        "PORTAL_SMOKE_SAME_WORLD_READY",
        "PORTAL_SMOKE_SERVER_SAME_WORLD_OK",
    ]
if scenario == "multiplayer":
    client_markers.append(
        "PORTAL_SMOKE_DYNAMIC_DIMENSION_RELOAD_OK"
        if reloaded else "PORTAL_SMOKE_DYNAMIC_DIMENSIONS_OK"
    )
    server_markers.append("PORTAL_SMOKE_SERVER_DYNAMIC_DIMENSIONS_OK")
    if reloaded:
        server_markers += [
            "PORTAL_SMOKE_DIMENSION_RELOAD_OK",
            "PORTAL_SMOKE_DYNAMIC_DIMENSION_STATE_OK",
        ]
if scenario == "rendering":
    client_markers += [
        "PORTAL_RENDER_FABRIC_CONTEXT_OK",
        "PORTAL_RENDER_FABRIC_CONTEXT_RELOAD_OK",
        "PORTAL_RENDER_ENVIRONMENT_OK",
        "PORTAL_RENDER_PLAYER_VISIBILITY_OK",
        "PORTAL_RENDER_OCCLUDED_OK",
        "PORTAL_RENDER_REVEALED_OK",
        "PORTAL_RENDER_NO_PREDICTION_OK",
        "PORTAL_RENDER_MODES_OK",
        "PORTAL_RENDER_WALL_STABLE_OK",
        "PORTAL_RENDER_COMPLETE",
    ]
    server_markers.append("PORTAL_RENDER_WALL_FIXTURE_READY")
    if modset in ("sodium", "iris"):
        client_markers.append("PORTAL_RENDER_SODIUM_COMPAT_OK")
    if modset == "iris":
        client_markers.append("PORTAL_RENDER_IRIS_COMPAT_OK")
else:
    client_markers += [
        "PORTAL_SMOKE_CONFIG_GUI_OK",
        "PORTAL_SMOKE_DIM_STACK_GUI_OK",
        "PORTAL_SMOKE_GUI_OK",
        "PORTAL_SMOKE_RENDER_OK",
        "PORTAL_SMOKE_SEAMLESS_CAMERA_OK",
        "PORTAL_SMOKE_TELEPORT_OK",
        "PORTAL_SMOKE_COMPLETE",
    ]
    if modset == "sodium":
        client_markers.append("PORTAL_SMOKE_SODIUM_COMPAT_OK")
    server_markers += [
        "PORTAL_SMOKE_SERVER_SCALED_TELEPORT_OK"
        if scenario == "scaled"
        else "PORTAL_SMOKE_SERVER_TELEPORT_OK"
    ]
    if scenario == "singleplayer":
        client_markers += [
            "PORTAL_SMOKE_CREATE_WORLD_GUI_OK",
            "PORTAL_SMOKE_DATAPACK_WORLDS_AND_NBT_OK",
            "PORTAL_SMOKE_SAME_WORLD_TELEPORT_OK",
        ]

for marker in client_markers:
    require(re.escape(marker), client, f"The client did not complete {marker}.")
for marker in server_markers:
    require(re.escape(marker), server, f"The server did not complete {marker}.")

if scenario != "rendering":
    movement_scopes = re.findall(
        r"PORTAL_SMOKE_SEAMLESS_START\b(.*?)PORTAL_SMOKE_SEAMLESS_CAMERA_OK", client, re.DOTALL)
    if not movement_scopes:
        raise AssertionError("The natural movement probe did not delimit its camera observations.")
    for scope in movement_scopes:
        forbidden = re.search(
            r"client player force teleported|Client player teleportation rejected|"
            r"Combo teleport out of limit|Adjusting Client Player Position", scope, re.IGNORECASE)
        if forbidden:
            raise AssertionError("The natural portal crossing required a correction: "
                                 + forbidden.group(0))

screenshots = Path("run/smoke-client/screenshots")
if not any(screenshots.glob("*.png")):
    raise AssertionError("The scenario produced no screenshot evidence.")

if scenario == "rendering":
    import base64
    import io
    from PIL import Image

    def fixture_thumbnail(rgb, prefix, name):
        # Only public smoke fixtures may be copied into job logs. Strip metadata and
        # keep each image small enough for the normal GitHub job-log API.
        thumbnail = Image.new("RGB", rgb.size)
        thumbnail.paste(rgb)
        thumbnail.thumbnail((512, 288), Image.Resampling.LANCZOS)
        while True:
            encoded = io.BytesIO()
            thumbnail.save(encoded, format="PNG", optimize=True)
            if encoded.tell() <= 28 * 1024:
                break
            thumbnail = thumbnail.resize(
                (max(1, thumbnail.width * 3 // 4), max(1, thumbnail.height * 3 // 4)),
                Image.Resampling.LANCZOS,
            )
        data = base64.b64encode(encoded.getvalue()).decode("ascii")
        print(f"{prefix} name={name} data={data}", flush=True)

    captures = sorted(screenshots.glob("portal-after-reload*.png"))
    if not captures:
        raise AssertionError("The rendering scenario did not capture the reloaded portal scene.")
    # These regions lie inside the fixed triangle and mirror fixtures. The lit destination
    # rooms and Overworld sky must contain visible content on every supported renderer.
    regions = {
        "triangle": (0.18, 0.30, 0.24, 0.46),
        "mirror": (0.44, 0.01, 0.56, 0.05),
    }
    metrics = {}
    with Image.open(captures[-1]) as capture:
        rgb = capture.convert("RGB")
        for name, region in regions.items():
            box = tuple(int(value * (rgb.width if index % 2 == 0 else rgb.height))
                        for index, value in enumerate(region))
            pixels = list(rgb.crop(box).getdata())
            if not pixels:
                raise AssertionError(f"The {name} fixture region contains no pixels.")
            black_fraction = sum(max(pixel) < 3 for pixel in pixels) / len(pixels)
            metrics[name] = {"box": box, "pixels": len(pixels), "black_fraction": black_fraction}

    # The server leaves stone exactly against the source plane and places an emerald
    # wall behind its destination. Each consecutive GPU frame must show the green
    # destination, including at a second angle and through the direct renderer. A
    # separate wall in front must still cover it, then reveal it again when removed.
    wall_metrics = {}
    wall_failures = []
    phases = ("prediction", "synchronous", "angled", "compatibility", "occluded", "revealed",
              "near-04-normal", "near-04-oblique", "near-01-normal", "near-01-oblique",
              "near-001-normal", "near-001-oblique", "near-direct",
              "near-micro-normal", "near-micro-oblique")
    for phase in phases:
        frames = re.findall(
            rf"PORTAL_RENDER_WALL_FRAME phase={phase} sample=(\d+) frame=(\d+)", client)
        if len(frames) != 12 or [int(sample) for sample, _ in frames] != list(range(12)):
            raise AssertionError(f"The {phase} wall probe did not capture its twelve frames: {frames}.")
        indices = [int(frame) for _, frame in frames]
        if any(second != first + 1 for first, second in zip(indices, indices[1:])):
            raise AssertionError(f"The {phase} wall probe skipped a rendered frame: {indices}.")
        measurements = []
        for sample in range(12):
            path = screenshots / f"portal-wall-{phase}-frame-{sample:02}.png"
            if not path.is_file():
                raise AssertionError(f"The {phase} wall probe did not save frame {sample}.")
            with Image.open(path) as capture:
                rgb = capture.convert("RGB")
                if sample == 0 and phase in ("prediction", "occluded", "revealed",
                                              "near-04-oblique", "near-001-oblique", "near-direct",
                                              "near-micro-oblique"):
                    fixture_thumbnail(rgb, "PORTAL_WALL_THUMBNAIL", phase)
                # This upper central patch lies well inside the fixed aperture, away
                # from the crosshair and from the portal's edges at both camera angles.
                box = (int(.46 * rgb.width), int(.42 * rgb.height),
                       int(.54 * rgb.width), int(.48 * rgb.height))
                pixels = list(rgb.crop(box).getdata())
            if not pixels:
                raise AssertionError("The coplanar portal witness contains no pixels.")
            green = sum(g > r * 1.18 + 8 and g > b * 1.05 + 3 and g >= 30
                        for r, g, b in pixels) / len(pixels)
            gray = sum(max(pixel) - min(pixel) < 25 and max(pixel) > 12
                       for pixel in pixels) / len(pixels)
            measurement = {"sample": sample, "frame": indices[sample], "box": box,
                           "green_fraction": green, "gray_fraction": gray}
            measurements.append(measurement)
            if phase == "occluded":
                if green > .05 or gray < .75:
                    wall_failures.append(f"The foreground wall exposed portal content: {measurement}")
            elif green < .8:
                wall_failures.append(f"The {phase} portal flashed its backing wall: {measurement}")
        wall_metrics[phase] = measurements

    source_metrics = {}
    for name in ("triangle", "mirror"):
        sources = sorted(screenshots.glob(f"portal-source-{name}*.png"))
        if not sources:
            source_metrics[name] = {"missing": True}
            continue
        with Image.open(sources[-1]) as source:
            rgb = source.convert("RGB")
            pixels = list(rgb.getdata())
            grid = []
            for row in range(6):
                cells = []
                for column in range(8):
                    box = (column * rgb.width // 8, row * rgb.height // 6,
                           (column + 1) * rgb.width // 8, (row + 1) * rgb.height // 6)
                    luminances = [0.2126 * r + 0.7152 * g + 0.0722 * b
                                  for r, g, b in rgb.crop(box).getdata()]
                    cells.append({"mean": round(sum(luminances) / len(luminances), 2),
                                  "max": round(max(luminances), 2)})
                grid.append(cells)
            source_metrics[name] = {
                "size": rgb.size,
                "black_fraction": sum(max(pixel) < 3 for pixel in pixels) / len(pixels),
                "luminance_grid_8x6": grid,
            }
            fixture_thumbnail(rgb, "PORTAL_SOURCE_THUMBNAIL", name)
    diagnostics = {"portal_regions": metrics, "native_sources": source_metrics,
                   "coplanar_wall_frames": wall_metrics}
    (evidence / "portal-pixel-metrics.json").write_text(json.dumps(diagnostics, indent=2) + "\n")
    # Emit diagnostics before a failure so the signed artifact download is not needed
    # to distinguish a black native world render from a portal-compositing regression.
    print("PORTAL_RENDER_PIXEL_METRICS " + json.dumps(diagnostics, sort_keys=True), flush=True)
    if wall_failures:
        raise AssertionError("\n".join(wall_failures))
    print(f"PORTAL_RENDER_WALL_PIXEL_CONTENT_OK phases={len(phases)} consecutive_frames_per_phase=12")
    for name, measurement in metrics.items():
        if measurement["black_fraction"] > 0.95:
            raise AssertionError(
                f"The {name} portal fixture is black after renderer reload: {measurement}."
            )
    print("PORTAL_RENDER_PIXEL_CONTENT_OK " + json.dumps(metrics, sort_keys=True))

print(f"FABRIC_RUNTIME_VERIFIED backend={backend} scenario={scenario} modset={modset} reload={reloaded}")
