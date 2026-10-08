#!/usr/bin/env bash
# Runs the app. JavaFX 21 needs JDK 17 or newer; the default JDK on this machine may be 11.
# Uses JAVA_HOME if it already points at a recent JDK, otherwise the newest sdkman JDK >= 17.
set -e
cd "$(dirname "$0")"

jdk_major() { "$1/bin/java" -version 2>&1 | sed -nE 's/.*version "([0-9]+).*/\1/p' | head -1; }

if [ -z "$JAVA_HOME" ] || [ "$(jdk_major "$JAVA_HOME" 2>/dev/null || echo 0)" -lt 17 ]; then
  best=""; best_major=0
  for c in "$HOME"/.sdkman/candidates/java/*/; do
    c="${c%/}"
    [ -x "$c/bin/java" ] || continue
    major="$(jdk_major "$c" 2>/dev/null || echo 0)"
    if [ "$major" -ge 17 ] && [ "$major" -gt "$best_major" ]; then best="$c"; best_major="$major"; fi
  done
  if [ -z "$best" ]; then
    echo "No JDK 17+ found. Set JAVA_HOME to a JDK 17 or newer." >&2
    exit 1
  fi
  export JAVA_HOME="$best"
fi
echo "Using JAVA_HOME=$JAVA_HOME"
exec mvn -q javafx:run "$@"
