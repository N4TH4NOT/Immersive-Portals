#!/usr/bin/env bash
# These scenarios execute real Minecraft clients exclusively on GitHub Actions.
set -euo pipefail

if [[ "${GITHUB_ACTIONS:-}" != true ]]; then
    printf '%s\n' 'Runtime verification is restricted to GitHub Actions.' >&2
    exit 2
fi

backend="${1:?Expected opengl or vulkan}"
scenario="${2:?Expected multiplayer, scaled, rendering or singleplayer}"
modset="${PORTAL_CI_MODSET:-vanilla}"
case "$backend" in opengl|vulkan) ;; *) exit 2 ;; esac
case "$scenario" in multiplayer|scaled|rendering|singleplayer) ;; *) exit 2 ;; esac
case "$modset" in vanilla|sodium|iris) ;; *) exit 2 ;; esac
if [[ "$modset" != vanilla && "$backend" != opengl ]] \
    || [[ "$modset" == iris && "$scenario" != rendering ]] \
    || [[ "$modset" == sodium && "$scenario" != rendering && "$scenario" != multiplayer ]]; then
    printf '%s\n' 'Compatibility verification requires OpenGL rendering or Sodium multiplayer.' >&2
    exit 2
fi

repo="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo"
evidence="$repo/build/ci/$backend-$scenario-$modset"
mkdir -p "$evidence"

if [[ "${3:-}" != --display-ready ]]; then
    export XDG_RUNTIME_DIR="${RUNNER_TEMP:?}/portal-$backend-$scenario"
    mkdir -p "$XDG_RUNTIME_DIR"
    chmod 700 "$XDG_RUNTIME_DIR"
    # Constrain Vulkan to lavapipe: a Vulkan-labelled job must never use a different ICD.
    lavapipe_candidates=(/usr/share/vulkan/icd.d/lvp_icd*.json)
    lavapipe="${lavapipe_candidates[0]}"
    if [[ ! -f "$lavapipe" ]]; then
        printf '%s\n' 'Mesa lavapipe ICD was not installed.' >&2
        exit 1
    fi
    export VK_DRIVER_FILES="$lavapipe"
    export VK_ICD_FILENAMES="$lavapipe"
    exec xvfb-run --auto-servernum --server-args='-screen 0 1280x720x24 -nolisten tcp' \
        bash "$0" "$backend" "$scenario" --display-ready
fi

server_pid=''
cleanup() {
    status=$?
    trap - EXIT INT TERM
    if [[ -n "$server_pid" ]]; then
        # GNU timeout forwards TERM to the complete Gradle/game process group.
        kill -TERM "$server_pid" 2>/dev/null || true
        wait "$server_pid" 2>/dev/null || true
    fi
    if [[ "$modset" == sodium ]]; then
        extractor_class=run/smoke-client/.mixin.out/class/net/minecraft/client/renderer/extract/LevelExtractor.class
        if [[ -f "$extractor_class" ]]; then
            javap -c -p "$extractor_class" > "$evidence/sodium-level-extractor-bytecode.txt" || true
            printf '%s\n' '::group::Sodium terrain update injection evidence'
            rg -n -B 5 -A 8 'ip_sodiumDirty|ip_propagateSodium|scheduleRebuild|setSectionRangeDirty' \
                "$evidence/sodium-level-extractor-bytecode.txt" || true
            printf '%s\n' '::endgroup::'
        fi
    fi
    if (( status != 0 )); then
        # Keep crash evidence readable through the job-log API when artifact delivery
        # is unavailable. Only the two isolated fixture-server consoles are eligible.
        for name in server-console.log reload-server-console.log; do
            if [[ -f "$evidence/$name" ]]; then
                printf '::group::Fixture server failure: %s\n' "$name"
                tail -n 300 "$evidence/$name"
                printf '%s\n' '::endgroup::'
            fi
        done
    fi
    printf 'Backend: %s\nScenario: %s\nMod set: %s\nExit status: %s\n' \
        "$backend" "$scenario" "$modset" "$status" > "$evidence/result.txt"
    if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
        printf '### Fabric %s / %s / %s\n\nExit status: `%s`. Console logs, game logs and screenshots are retained in the runtime artifact.\n\n' \
            "$backend" "$scenario" "$modset" "$status" >> "$GITHUB_STEP_SUMMARY"
    fi
    exit "$status"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

glxinfo -B > "$evidence/opengl-driver.txt" 2>&1
vulkaninfo --summary > "$evidence/vulkan-driver.txt" 2>&1
cat "$evidence/opengl-driver.txt"
cat "$evidence/vulkan-driver.txt"
chmod +x gradlew

mkdir -p run/smoke-client run/smoke-server
cat > run/smoke-client/options.txt <<OPTIONS
preferredGraphicsBackend:"$backend"
onboardAccessibility:false
narrator:0
skipMultiplayerWarning:true
pauseOnLostFocus:false
fullscreen:false
maxFps:30
renderDistance:3
simulationDistance:5
graphicsPreset:"fast"
ao:true
autoJump:false
OPTIONS
printf 'eula=true\n' > run/smoke-server/eula.txt
cp src/smokeTest/resources/server.properties run/smoke-server/server.properties
python3 - <<'PY'
from pathlib import Path
import secrets

properties = Path('run/smoke-server/server.properties')
with properties.open('a') as stream:
    stream.write('\nenable-rcon=true\nrcon.port=25575\nbroadcast-rcon-to-ops=false\n')
    stream.write('rcon.password=' + secrets.token_hex(24) + '\n')
properties.chmod(0o600)
PY

username=PortalTest
scenario_args=()
case "$scenario" in
    multiplayer) scenario_args+=(-PportalSmokeDynamicDimensions=true) ;;
    scaled) username=ScaledTest ;;
    rendering) username=OcclusionTest; scenario_args+=(-PportalRenderSmoke=true) ;;
    singleplayer) scenario_args+=(-PportalSmokeSingleplayer=true) ;;
esac
case "$modset" in
    sodium) scenario_args+=(-PportalSmokeSodium=true) ;;
    iris) scenario_args+=(-PportalSmokeIris=true) ;;
esac

# Resolve and compile before starting two long-lived Gradle processes against the checkout.
./gradlew -PportalSmoke compileSmokeTestJava --no-daemon --stacktrace --console=plain \
    2>&1 | tee "$evidence/prepare.log"

start_server() {
    server_log="$1"
    shift
    timeout --signal=TERM --kill-after=30s 900s \
        ./gradlew -PportalSmoke runServer --no-daemon --stacktrace --console=plain \
        --project-cache-dir "${RUNNER_TEMP:?}/portal-server-gradle" "$@" \
        > "$server_log" 2>&1 &
    server_pid=$!
    deadline=$((SECONDS + 240))
    until grep -Fq 'PORTAL_SMOKE_SERVER_READY' "$server_log"; do
        if ! kill -0 "$server_pid" 2>/dev/null; then
            cat "$server_log"
            printf '%s\n' 'The verification server exited before becoming ready.' >&2
            exit 1
        fi
        if (( SECONDS >= deadline )); then
            cat "$server_log"
            printf '%s\n' 'The verification server did not become ready within four minutes.' >&2
            exit 1
        fi
        sleep 2
    done
}

stop_server() {
    python3 .github/ci/stop-fabric-server.py run/smoke-server/server.properties
    deadline=$((SECONDS + 60))
    while kill -0 "$server_pid" 2>/dev/null; do
        if (( SECONDS >= deadline )); then
            printf '%s\n' 'The verification server did not finish its graceful shutdown.' >&2
            exit 1
        fi
        sleep 1
    done
    wait "$server_pid"
    server_pid=''
    if ! grep -Fq 'All dimensions are saved' "$server_log"; then
        printf '%s\n' 'The server did not confirm that every dimension was saved.' >&2
        exit 1
    fi
}

if [[ "$scenario" != singleplayer ]]; then
    start_server "$evidence/server-console.log"
fi

run_client() {
    client_log="$1"
    shift
    timeout --signal=TERM --kill-after=30s 600s \
        ./gradlew -PportalSmoke "-PportalSmokeBackend=$backend" \
            "-PportalSmokeUsername=$username" "${scenario_args[@]}" "$@" \
            runClient --no-daemon --stacktrace --console=plain \
        2>&1 | tee "$client_log"
}

run_client "$evidence/client-console.log"

/usr/bin/python3 .github/ci/assert-fabric-scenario.py "$backend" "$scenario" "$evidence"

if [[ "$scenario" == multiplayer ]]; then
    stop_server
    # Loading without the registration option proves that saved dimensions survive restart.
    python3 - <<'PY'
import json
from pathlib import Path

config = Path('run/smoke-server/config/immersive_portals.json')
settings = json.loads(config.read_text())
settings['enableAlternateDimensions'] = False
config.write_text(json.dumps(settings, indent=2) + '\n')
PY
    start_server "$evidence/reload-server-console.log" -PportalSmokeDimensionReload=true
    grep -F 'PORTAL_SMOKE_DIMENSION_RELOAD_OK' "$evidence/reload-server-console.log"
    grep -F 'PORTAL_SMOKE_SKYLAND_WORLD_SEED_OK' "$evidence/reload-server-console.log"
    run_client "$evidence/reload-client-console.log" -PportalSmokeDimensionReload=true
    /usr/bin/python3 .github/ci/assert-fabric-scenario.py "$backend" "$scenario" "$evidence" --reload
    stop_server
    printf '%s\n' 'FABRIC_DIMENSION_RESTART_VERIFIED' | tee "$evidence/dimension-restart.txt"
elif [[ "$scenario" != singleplayer ]]; then
    stop_server
fi
