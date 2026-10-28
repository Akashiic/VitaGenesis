#!/usr/bin/env bash
# Runs the VitaGenesis self test (bootstrap -> full -> reload, see tools/selftest.sh) on a copy of a real Crucible
# server (server.jar, mods/, plugins/, config/, agents), with the production command line.
#
#   tools/crucibletest.sh <server dir>
#
# The copy must be marked with a .bellow-test-copy file (the world is deleted, as in Below Core's harness) and must
# already hold the reobfuscated Below Core and VitaGenesis jars in mods/. Config files are the copy's own (Et Futurum's
# experiments included): what is tested is what production would run. Below Core's generation is switched off for the
# bootstrap phase and back on afterwards in config/akashicbellow.cfg.
# Reports: <dir>/vitagenesis-selftest-<phase>.txt; logs: <dir>/vitacrucible-<phase>.log.
# JVM flags follow Below Core's tools/crucibletest.sh (heap HEAP, default 9216M; young generation 5..30 %; stdin an open
# pipe that never sends anything, because Crucible's console reader spins on end-of-file).
set -uo pipefail
dir=${1:?server dir}
cd "$dir" || exit 2
if [ ! -f .bellow-test-copy ]; then
  echo "refusing: $PWD has no .bellow-test-copy marker (this script deletes world/). Use a copy of the server." >&2
  exit 2
fi
level=$(sed -n 's/^level-name=//p' server.properties 2>/dev/null | tail -1)
level=${level:-world}
case "$level" in */*|.*|"") echo "refusing: unexpected level-name '$level'" >&2; exit 2 ;; esac
rm -rf -- "$level" crash-reports vitagenesis-selftest*.txt
agents=()
for a in *-agent.jar; do [ -f "$a" ] && agents+=("-javaagent:$PWD/$a"); done
JAVA=${JAVA:-java}
cfg=config/akashicbellow.cfg

run() {
  local phase=$1
  exec 3< <(exec sleep 86400)
  local sleeper=$!
  timeout "${TIMEOUT:-3600}" "$JAVA" -Duser.timezone=America/Sao_Paulo -Dterminal.jline=false -Dterminal.ansi=true \
    -Dfile.encoding=UTF8 -Xms"${HEAP:-9216M}" -Xmx"${HEAP:-9216M}" -XX:+UseG1GC -XX:+ParallelRefProcEnabled \
    -XX:MaxGCPauseMillis=200 -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC \
    -XX:G1NewSizePercent="${G1NEW:-5}" -XX:G1MaxNewSizePercent="${G1MAXNEW:-30}" -XX:G1HeapRegionSize=16M \
    -XX:G1ReservePercent=15 -XX:G1HeapWastePercent=5 -XX:G1MixedGCCountTarget=4 \
    -XX:InitiatingHeapOccupancyPercent=20 -XX:G1MixedGCLiveThresholdPercent=90 -XX:G1RSetUpdatingPauseTimePercent=5 \
    -XX:SurvivorRatio=32 -XX:+PerfDisableSharedMem -XX:MaxTenuringThreshold=1 "${agents[@]}" @java9args.txt \
    -Dfml.doNotBackup=true -Dfml.queryResult=confirm -Dvitagenesis.selftest=true -Dvitagenesis.selftest.exit=true \
    -Dvitagenesis.selftest.phase="$phase" -jar server.jar nogui > "vitacrucible-$phase.log" 2>&1 <&3
  local rc=$?
  kill "$sleeper" 2>/dev/null
  exec 3<&-
  echo "== phase $phase (rc=$rc)"
  cat "vitagenesis-selftest-$phase.txt" 2>/dev/null || { echo "no report (see $PWD/vitacrucible-$phase.log)"; return 1; }
}
set_bellow() { [ -f "$cfg" ] && sed -i "/^worldgen {/,/^}/ s/B:enabled=.*/B:enabled=$1/" "$cfg"; }

# the config file exists after the first launch: create it, then start over from a new world
if [ ! -f "$cfg" ]; then run bootstrap || true; rm -rf -- "$level" vitagenesis-selftest*.txt; fi
set_bellow false; run bootstrap || exit 1
set_bellow true;  run full || exit 1
run reload || exit 1
! grep -q "^RESULT FAIL" vitagenesis-selftest-*.txt
