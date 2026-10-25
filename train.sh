#!/usr/bin/env bash
# Train the model from command line. Example: ./train.sh --epochs 8 --hidden 64,32
set -euo pipefail
cd "$(dirname "$0")"
if [ ! -f out/classes/digitai/Main.class ]; then
  ./build.sh
fi
exec java -Dstdout.encoding=UTF-8 -cp out/classes digitai.Main train "$@"
