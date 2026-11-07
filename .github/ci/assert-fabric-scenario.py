#!/usr/bin/env python3
"""Require game assertions and the actual backend; a clean launch alone is insufficient."""
import os
from pathlib import Path
import re
import sys

if os.environ.get("GITHUB_ACTIONS") != "true":
    raise SystemExit("Automated validation is restricted to GitHub Actions.")

backend, scenario, evidence_path = sys.argv[1:]
evidence = Path(evidence_path)
client = (evidence / "client-console.log").read_text(errors="replace")
server = (
    client
    if scenario == "singleplayer"
    else (evidence / "server-console.log").read_text(errors="replace")
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
    "PORTAL_SMOKE_ITEM_MODELS_OK",
    "PORTAL_SMOKE_PERFORMANCE_OK",
    "PORTAL_SMOKE_MIGRATION_RESOURCES_OK",
]
server_markers = ["PORTAL_SMOKE_ALTERNATE_DIMENSIONS_OK", "PORTAL_SMOKE_SERVER_READY"]
if scenario == "rendering":
    client_markers += [
        "PORTAL_RENDER_PLAYER_VISIBILITY_OK",
        "PORTAL_RENDER_OCCLUDED_OK",
        "PORTAL_RENDER_REVEALED_OK",
        "PORTAL_RENDER_NO_PREDICTION_OK",
        "PORTAL_RENDER_MODES_OK",
        "PORTAL_RENDER_COMPLETE",
    ]
else:
    client_markers += [
        "PORTAL_SMOKE_CONFIG_GUI_OK",
        "PORTAL_SMOKE_DIM_STACK_GUI_OK",
        "PORTAL_SMOKE_GUI_OK",
        "PORTAL_SMOKE_RENDER_OK",
        "PORTAL_SMOKE_TELEPORT_OK",
        "PORTAL_SMOKE_COMPLETE",
    ]
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

screenshots = Path("run/smoke-client/screenshots")
if not any(screenshots.glob("*.png")):
    raise AssertionError("The scenario produced no screenshot evidence.")

print(f"FABRIC_RUNTIME_VERIFIED backend={backend} scenario={scenario}")
