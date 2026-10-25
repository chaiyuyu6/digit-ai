#!/usr/bin/env bash
# Start DigitLab GUI
set -euo pipefail
cd "$(dirname "$0")"
if [ ! -f digit-ai.jar ]; then
  ./build.sh
fi
exec java -jar digit-ai.jar
