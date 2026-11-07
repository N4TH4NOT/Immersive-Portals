#!/usr/bin/env python3
"""Verify two real clients, remote pickups, entity physics and persistence."""
import os
from pathlib import Path
import re
import sys

if os.environ.get("GITHUB_ACTIONS") != "true":
    raise SystemExit("Automated validation is restricted to GitHub Actions.")

backend, directory, prefix = sys.argv[1:4]
evidence = Path(directory)
server = (evidence / f"{prefix}server-console.log").read_text(errors="replace")
failure = (r"AssertionError|MixinApplyError|InvalidMixinException|MixinTransformerError|"
           r"Exception in thread|Could not execute entrypoint|This crash report has been saved|"
           r"Failed to handle packet|Network Protocol Error|Error executing task on Client")
if re.search(failure, server):
    raise AssertionError("The two-player fixture server reported a runtime failure.")
reloaded = bool(prefix)
required = ["SERVER_COMPLETE phase=rejoin" if reloaded else "SERVER_COMPLETE phase=save"]
if reloaded:
    required += ["PORTALS_RELOADED_OK", "PERSISTENCE_OK", "CONCURRENT_CROSSING_OK phase=9"]
else:
    required += ["FIXTURE_READY", "CONCURRENT_CROSSING_OK phase=4",
                 "CONCURRENT_CROSSING_OK phase=5", "SERVER_PERFORMANCE_OK",
                 "PERSISTENCE_SAVED", "ENTITY_UUID_OK", "PROJECTILE_WORLD_OK",
                 "HURTING_PROJECTILE_OK", "THROWABLE_PROJECTILE_OK",
                 "ENTITY_PHYSICS_OK count=7 destinationHits=2"]
    required += [f"PICKUP_{phase}_OK" for phase in
                 ("APPROACH", "DELAY", "CAPACITY", "OWNERSHIP", "APERTURE", "REACH", "RACE", "CROSS_WORLD")]
    required.append("PICKUP_OK")
    for kind in ("mob", "item", "projectile", "fast_projectile", "cross_world_mob",
                 "cross_world_projectile", "cross_world_fireball"):
        required.append(f"ENTITY_CROSSED type={kind}")
    required += [f"PROJECTILE_DESTINATION_HIT type={kind}"
                 for kind in ("projectile", "fast_projectile")]
for marker in required:
    if "PORTAL_QUALITY_" + marker not in server:
        raise AssertionError(f"Missing two-player server evidence: {marker}")
for role in ("a", "b"):
    name = "QualityA" if role == "a" else "QualityB"
    client = (evidence / f"{prefix}client-{role}-console.log").read_text(errors="replace")
    if re.search(failure, client):
        raise AssertionError(f"The real {name} client reported a runtime failure.")
    actual = "Vulkan" if backend == "vulkan" else "OpenGL"
    if not re.search(rf"Using graphics backend {actual}\b", client):
        raise AssertionError(f"The real {name} client used the wrong backend.")
    markers = [f"CAMERA_OK player={name}", f"TWO_PLAYERS_OK player={name}",
               f"COMPLETE player={name} phase=" + ("rejoin" if reloaded else "save")]
    if os.environ.get("PORTAL_CI_MODSET") == "sodium":
        markers.append("SODIUM_COMPAT_OK")
    if not reloaded:
        markers.append(f"CLIENT_PERFORMANCE_OK player={name}")
        if role == "a": markers.append("PICKUP_ANIMATION_OK")
    for marker in markers:
        if "PORTAL_QUALITY_" + marker not in client:
            raise AssertionError(f"Missing {name} evidence: {marker}")
    start = client.find("PORTAL_QUALITY_CLIENT_STAGE")
    stop = client.find(f"PORTAL_QUALITY_COMPLETE player={name}")
    scope = client[start:stop]
    if start < 0 or stop < start or re.search(
            r"client player force teleported|Client player teleportation rejected|"
            r"Combo teleport out of limit|Adjusting Client Player Position", scope, re.IGNORECASE):
        raise AssertionError(f"The real {name} trajectory needed a position correction.")
print(f"FABRIC_QUALITY_ROUND_VERIFIED backend={backend} reload={reloaded}", flush=True)
