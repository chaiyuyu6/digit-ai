#!/usr/bin/env bash
# DigitLab - macOS / Linux / Git Bash build script (requires JDK 17+)
set -euo pipefail
cd "$(dirname "$0")"

if ! command -v javac >/dev/null 2>&1; then
  echo "[ERROR] javac not found. Please install JDK 17+ and add it to PATH." >&2
  exit 1
fi

rm -rf out/classes
mkdir -p out/classes
find src/main/java -name '*.java' > out/sources.txt
echo "Compiling..."
javac -encoding UTF-8 -d out/classes @out/sources.txt
jar --create --file digit-ai.jar --main-class digitai.Main -C out/classes .

echo
echo "Build OK: digit-ai.jar"
echo "  GUI    : ./run.sh"
echo "  Train  : ./train.sh --epochs 8"
