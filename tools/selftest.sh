#!/usr/bin/env bash
# Headless end-to-end verification on a dedicated dev server with Below Core and Et Futurum Requiem.
#   1. bootstrap: Below Core generation off -> area A is a 1.7.10 world without deep layer ("pre-generated")
#   2. full:      generation on -> area A gets its deep layer and caves from disk (retrogen), area B is new;
#                 both are measured and checked, a checksum of their deep layer is written
#   3. reload:    a fresh JVM reads both areas back and compares the checksum and the lit marks
# Usage: tools/selftest.sh [floor, default -64] [extra gradle args]
# Needs a Below Core checkout built with ./gradlew build in ../BelowCore (or -Pbellow.dir=<path>).
set -euo pipefail
cd "$(dirname "$0")/.."
FLOOR="${1:--64}"
shift || true
RUN=run/server
BELLOW_CFG=$RUN/config/akashicbellow.cfg
mkdir -p "$RUN" build
echo "eula=true" > "$RUN/eula.txt"
printf "online-mode=false\nspawn-protection=0\nlevel-seed=vitagenesis\nmax-tick-time=-1\n" > "$RUN/server.properties"
rm -rf "$RUN/world" "$RUN/crash-reports" "$RUN"/vitagenesis-selftest*.txt
rm -f "$RUN/config/vitagenesis.cfg" # the defaults are what is tested

# Below Core's generation switch and floor; the config file exists after the first launch
set_bellow() {
  [ -f "$BELLOW_CFG" ] || return 0
  sed -i "/^worldgen {/,/^}/ s/B:enabled=.*/B:enabled=$1/" "$BELLOW_CFG"
  sed -i "s/I:minY=.*/I:minY=$FLOOR/" "$BELLOW_CFG"
}
# Et Futurum's moss/azalea and dripstone are experiments, off by default: on for the test (the pack decides for itself)
efr_experiments() {
  local f=$RUN/config/etfuturum/experiments.cfg
  [ -f "$f" ] && sed -i -e "s/B:enableMossAzalea=.*/B:enableMossAzalea=true/" -e "s/B:enableDripstone=.*/B:enableDripstone=true/" "$f" || true
}
phase() {
  ./gradlew --no-daemon -q runServer21 -Pvita.selftest=true -Pvita.phase="$1" "${@:2}" > "build/selftest-$1.log" 2>&1 || true
  echo "== phase $1"
  cat "$RUN/vitagenesis-selftest-$1.txt" 2>/dev/null || { echo "no report (see build/selftest-$1.log)"; return 1; }
}

if [ ! -f "$BELLOW_CFG" ]; then phase bootstrap "$@" || true; rm -rf "$RUN/world" "$RUN"/vitagenesis-selftest*.txt; fi
efr_experiments
set_bellow false; phase bootstrap "$@"
set_bellow true;  phase full "$@"
phase reload "$@"
grep -h "^RESULT\|^FAIL" "$RUN"/vitagenesis-selftest-*.txt || true
! grep -q "^RESULT FAIL" "$RUN"/vitagenesis-selftest-*.txt
