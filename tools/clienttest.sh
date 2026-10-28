#!/usr/bin/env bash
# Headless client verification under Xvfb (Mesa llvmpipe): a singleplayer world, the camera inside a lush and a
# dripstone cave; client and server must agree on blocks, block light and glow lichen faces. Screenshots in
# run/client/screenshots/. Et Futurum's experiments config is taken from the server test (tools/selftest.sh) so both
# register the same blocks. Extra gradle args are forwarded.
set -uo pipefail
cd "$(dirname "$0")/.."
mkdir -p build run/client/config/etfuturum
if [ -f run/server/config/etfuturum/experiments.cfg ]; then
  cp run/server/config/etfuturum/experiments.cfg run/client/config/etfuturum/experiments.cfg
fi
rm -f run/client/vitagenesis-clienttest.txt run/client/screenshots/vita-*.png
xvfb-run -a -s "-screen 0 1280x720x24" ./gradlew --no-daemon -q runClient21 -Pvita.clienttest=true "$@" > build/clienttest.log 2>&1
report=run/client/vitagenesis-clienttest.txt
[ -f "$report" ] || { echo "no report (see build/clienttest.log)"; exit 1; }
cat "$report"
! grep -q "RESULT FAIL" "$report"
