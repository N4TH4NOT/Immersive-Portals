#!/usr/bin/env bash
# These scenarios execute real Minecraft clients exclusively on GitHub Actions.
set -euo pipefail

if [[ "${GITHUB_ACTIONS:-}" != true ]]; then
    printf '%s\n' 'Runtime verification is restricted to GitHub Actions.' >&2
    exit 2
fi

backend="${1:?Expected opengl or vulkan}"
scenario="${2:?Expected multiplayer, scaled, rendering, optics, interaction, quality or singleplayer}"
modset="${PORTAL_CI_MODSET:-vanilla}"
case "$backend" in opengl|vulkan) ;; *) exit 2 ;; esac
case "$scenario" in multiplayer|scaled|rendering|optics|interaction|quality|singleplayer) ;; *) exit 2 ;; esac
case "$modset" in vanilla|sodium|iris) ;; *) exit 2 ;; esac
if [[ "$modset" != vanilla && "$backend" != opengl ]] \
    || [[ "$modset" == iris && "$scenario" != rendering && "$scenario" != optics ]] \
    || [[ "$modset" == sodium && "$scenario" != rendering && "$scenario" != optics && "$scenario" != multiplayer && "$scenario" != interaction && "$scenario" != quality ]]; then
    printf '%s\n' 'Compatibility verification requires OpenGL graphics or Sodium multiplayer.' >&2
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
client_pids=()
quality_workspace_root=''
isolated_client_workspace=''

collect_quality_evidence() {
    [[ "$scenario" == quality && -n "$quality_workspace_root" ]] || return 0
    local quality_role source_directory destination_directory
    for quality_role in a b; do
        source_directory="$quality_workspace_root/$quality_role/run/smoke-client-$quality_role"
        destination_directory="$repo/run/smoke-client-$quality_role"
        if [[ -d "$source_directory" ]]; then
            mkdir -p "$destination_directory" || return 1
            # Include hidden Mixin exports alongside game logs, options and screenshots.
            cp -a "$source_directory/." "$destination_directory/" || return 1
        fi
    done
}

collect_isolated_client_evidence() {
    [[ -n "$isolated_client_workspace" ]] || return 0
    local source_directory="$isolated_client_workspace/run/smoke-client"
    if [[ -d "$source_directory" ]]; then
        mkdir -p "$repo/run/smoke-client" || return 1
        cp -a "$source_directory/." "$repo/run/smoke-client/" || return 1
    fi
}

cleanup() {
    status=$?
    trap - EXIT INT TERM
    cd "$repo"
    for client_pid in "${client_pids[@]}"; do
        pkill -TERM -P "$client_pid" 2>/dev/null || true
        kill -TERM "$client_pid" 2>/dev/null || true
        wait "$client_pid" 2>/dev/null || true
    done
    if [[ -n "$server_pid" ]]; then
        # GNU timeout forwards TERM to the complete Gradle/game process group.
        kill -TERM "$server_pid" 2>/dev/null || true
        wait "$server_pid" 2>/dev/null || true
    fi
    if ! collect_quality_evidence; then
        printf '%s\n' 'Could not collect the isolated quality clients game evidence.' >&2
        if (( status == 0 )); then status=1; fi
    fi
    if ! collect_isolated_client_evidence; then
        printf '%s\n' 'Could not collect the isolated fixture client game evidence.' >&2
        if (( status == 0 )); then status=1; fi
    fi
    if [[ "$modset" == sodium ]]; then
        extractor_class=run/smoke-client/.mixin.out/class/net/minecraft/client/renderer/extract/LevelExtractor.class
        if [[ "$scenario" == quality ]]; then
            extractor_class=run/smoke-client-a/.mixin.out/class/net/minecraft/client/renderer/extract/LevelExtractor.class
        fi
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
    optics) username=OpticsTest; scenario_args+=(-PportalOpticsSmoke=true) ;;
    interaction) username=InteractionTest; scenario_args+=(-PportalSmokeNativeInteractions=true) ;;
    quality) scenario_args+=(-PportalQualitySmoke=true) ;;
    singleplayer) scenario_args+=(-PportalSmokeSingleplayer=true) ;;
esac
case "$modset" in
    sodium) scenario_args+=(-PportalSmokeSodium=true) ;;
    iris) scenario_args+=(-PportalSmokeIris=true) ;;
esac

# Resolve assets serially before any long-lived game process can read the shared asset cache.
./gradlew -PportalSmoke compileSmokeTestJava downloadAssets --no-daemon --stacktrace --console=plain \
    2>&1 | tee "$evidence/prepare.log"

prepare_quality_workspaces() {
    quality_workspace_root="$(mktemp -d "${RUNNER_TEMP:?}/portal-quality-$backend-$modset.XXXXXX")"
    local archive="$quality_workspace_root/source.tar"
    local quality_role workspace quality_username quality_directory
    # The populated gravity submodule is ordinary source here. Exclude generated
    # outputs at every depth, including the composite build's classes and jars.
    tar -C "$repo" --exclude=.git --exclude=.gradle --exclude=build --exclude=run \
        -cf "$archive" .
    for quality_role in a b; do
        workspace="$quality_workspace_root/$quality_role"
        mkdir -p "$workspace"
        tar -C "$workspace" -xf "$archive"
        chmod +x "$workspace/gradlew"
        quality_directory="run/smoke-client-$quality_role"
        mkdir -p "$workspace/$quality_directory"
        cp "$repo/run/smoke-client/options.txt" "$workspace/$quality_directory/options.txt"
        if [[ "$quality_role" == a ]]; then quality_username=QualityA; else quality_username=QualityB; fi
        # Prepare each independent project before starting the root server. Its
        # own output tree and execution history cannot delete another game's classes.
        (
            cd "$workspace"
            ./gradlew -PportalSmoke "${scenario_args[@]}" \
                "-PportalSmokeUsername=$quality_username" \
                "-PportalSmokeClientDir=$quality_directory" \
                --project-cache-dir "$workspace/.gradle" \
                compileSmokeTestJava downloadAssets --no-daemon --stacktrace --console=plain
        ) 2>&1 | tee "$evidence/prepare-client-$quality_role.log"
        printf 'PORTAL_QUALITY_WORKSPACE_READY role=%s\n' "$quality_role"
    done
    rm -- "$archive"
}

if [[ "$scenario" == quality ]]; then
    prepare_quality_workspaces
elif [[ "$scenario" != singleplayer ]]; then
    isolated_client_workspace="$(mktemp -d "${RUNNER_TEMP:?}/portal-client-$backend-$scenario-$modset.XXXXXX")"
    # A client's variant-dependent compile may replace build/classes while the
    # dedicated server lazily loads another class. Separate execution histories
    # alone do not isolate Loom's output tree, including the gravity composite.
    tar -C "$repo" --exclude=.git --exclude=.gradle --exclude=build --exclude=run \
        -cf - . | tar -C "$isolated_client_workspace" -xf -
    chmod +x "$isolated_client_workspace/gradlew"
    mkdir -p "$isolated_client_workspace/run/smoke-client"
    cp "$repo/run/smoke-client/options.txt" "$isolated_client_workspace/run/smoke-client/options.txt"
    (
        cd "$isolated_client_workspace"
        ./gradlew -PportalSmoke "-PportalSmokeBackend=$backend" \
            "-PportalSmokeUsername=$username" "${scenario_args[@]}" \
            compileSmokeTestJava downloadAssets --no-daemon --stacktrace --console=plain
    ) 2>&1 | tee "$evidence/prepare-client.log"
    printf '%s\n' 'PORTAL_CLIENT_WORKSPACE_READY'
fi

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
    if [[ "$scenario" == quality ]]; then
        start_server "$evidence/server-console.log" -PportalQualitySmoke=true
    else
        start_server "$evidence/server-console.log"
    fi
fi

run_client() {
    local client_log="$1"
    shift
    local client_task=runClient
    local client_workspace="$repo"
    if [[ -n "$isolated_client_workspace" ]]; then
        client_workspace="$isolated_client_workspace"
    fi
    if [[ "$scenario" == quality ]]; then
        # --project-cache-dir isolates launch.cfg, but Loom also writes a
        # classpath argfile under build/loom-cache/argFiles/<task name>.
        case "$username" in
            QualityA) client_task=runQualityA; client_workspace="$quality_workspace_root/a" ;;
            QualityB) client_task=runQualityB; client_workspace="$quality_workspace_root/b" ;;
            *) printf '%s\n' 'The quality client has no isolated Loom run task.' >&2; return 2 ;;
        esac
    fi
    # Quality calls run in independent background shells. The timeout remains
    # their direct child so cleanup can terminate the complete game process group.
    cd "$client_workspace"
    local client_status=0
    timeout --signal=TERM --kill-after=30s 600s \
        ./gradlew -PportalSmoke "-PportalSmokeBackend=$backend" \
            "-PportalSmokeUsername=$username" "${scenario_args[@]}" "$@" \
            "$client_task" --no-daemon --stacktrace --console=plain \
        2>&1 | tee "$client_log" || client_status=$?
    cd "$repo"
    return "$client_status"
}

run_quality_clients() {
    quality_prefix="$1"
    shift
    client_pids=()
    for quality_role in a b; do
        quality_directory="run/smoke-client-$quality_role"
        mkdir -p "$quality_workspace_root/$quality_role/$quality_directory"
        cp "$repo/run/smoke-client/options.txt" \
            "$quality_workspace_root/$quality_role/$quality_directory/options.txt"
        if [[ "$quality_role" == a ]]; then username=QualityA; else username=QualityB; fi
        run_client "$evidence/${quality_prefix}client-$quality_role-console.log" \
            "-PportalSmokeClientDir=$quality_directory" \
            --project-cache-dir "$quality_workspace_root/$quality_role/.gradle" "$@" &
        client_pids+=("$!")
    done
    for client_pid in "${client_pids[@]}"; do wait "$client_pid"; done
    client_pids=()
    collect_quality_evidence
    /usr/bin/python3 .github/ci/assert-quality.py "$backend" "$evidence" "$quality_prefix"
}

if [[ "$scenario" == quality ]]; then
    run_quality_clients ''
    stop_server
    start_server "$evidence/reload-server-console.log" \
        -PportalQualitySmoke=true -PportalQualityReload=true
    run_quality_clients 'reload-' -PportalQualityReload=true
    stop_server
    printf '%s\n' 'FABRIC_QUALITY_RESTART_VERIFIED'
    exit 0
fi

run_client "$evidence/client-console.log"
collect_isolated_client_evidence

if [[ "$scenario" == optics ]]; then
    /usr/bin/python3 .github/ci/assert-optics.py "$backend" "$evidence"
elif [[ "$scenario" == interaction ]]; then
    /usr/bin/python3 .github/ci/assert-native-interaction.py "$backend" "$evidence"
else
    /usr/bin/python3 .github/ci/assert-fabric-scenario.py "$backend" "$scenario" "$evidence"
fi

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
    collect_isolated_client_evidence
    /usr/bin/python3 .github/ci/assert-fabric-scenario.py "$backend" "$scenario" "$evidence" --reload
    stop_server
    printf '%s\n' 'FABRIC_DIMENSION_RESTART_VERIFIED' | tee "$evidence/dimension-restart.txt"
elif [[ "$scenario" != singleplayer ]]; then
    stop_server
fi
