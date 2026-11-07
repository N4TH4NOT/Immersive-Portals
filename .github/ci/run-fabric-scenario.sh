#!/usr/bin/env bash
# These scenarios execute real Minecraft clients exclusively on GitHub Actions.
set -euo pipefail

if [[ "${GITHUB_ACTIONS:-}" != true ]]; then
    printf '%s\n' 'Runtime verification is restricted to GitHub Actions.' >&2
    exit 2
fi

backend="${1:?Expected opengl or vulkan}"
scenario="${2:?Expected multiplayer, scaled, rendering or singleplayer}"
case "$backend" in opengl|vulkan) ;; *) exit 2 ;; esac
case "$scenario" in multiplayer|scaled|rendering|singleplayer) ;; *) exit 2 ;; esac

repo="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo"
evidence="$repo/build/ci/$backend-$scenario"
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
    printf 'Backend: %s\nScenario: %s\nExit status: %s\n' \
        "$backend" "$scenario" "$status" > "$evidence/result.txt"
    if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
        printf '### Fabric %s / %s\n\nExit status: `%s`. Console logs, game logs and screenshots are retained in the runtime artifact.\n\n' \
            "$backend" "$scenario" "$status" >> "$GITHUB_STEP_SUMMARY"
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
simulationDistance:3
graphicsPreset:"fast"
autoJump:false
OPTIONS
printf 'eula=true\n' > run/smoke-server/eula.txt

username=PortalTest
scenario_args=()
case "$scenario" in
    scaled) username=ScaledTest ;;
    rendering) username=OcclusionTest; scenario_args+=(-PportalRenderSmoke=true) ;;
    singleplayer) scenario_args+=(-PportalSmokeSingleplayer=true) ;;
esac

# Resolve and compile before starting two long-lived Gradle processes against the checkout.
./gradlew -PportalSmoke compileSmokeTestJava --no-daemon --stacktrace --console=plain \
    2>&1 | tee "$evidence/prepare.log"

if [[ "$scenario" != singleplayer ]]; then
    timeout --signal=TERM --kill-after=30s 900s \
        ./gradlew -PportalSmoke runServer --no-daemon --stacktrace --console=plain \
        --project-cache-dir "${RUNNER_TEMP:?}/portal-server-gradle" \
        > "$evidence/server-console.log" 2>&1 &
    server_pid=$!
    deadline=$((SECONDS + 240))
    until grep -Fq 'PORTAL_SMOKE_SERVER_READY' "$evidence/server-console.log"; do
        if ! kill -0 "$server_pid" 2>/dev/null; then
            cat "$evidence/server-console.log"
            printf '%s\n' 'The verification server exited before becoming ready.' >&2
            exit 1
        fi
        if (( SECONDS >= deadline )); then
            cat "$evidence/server-console.log"
            printf '%s\n' 'The verification server did not become ready within four minutes.' >&2
            exit 1
        fi
        sleep 2
    done
fi

timeout --signal=TERM --kill-after=30s 600s \
    ./gradlew -PportalSmoke "-PportalSmokeBackend=$backend" \
        "-PportalSmokeUsername=$username" "${scenario_args[@]}" \
        runClient --no-daemon --stacktrace --console=plain \
    2>&1 | tee "$evidence/client-console.log"

python3 .github/ci/assert-fabric-scenario.py "$backend" "$scenario" "$evidence"
