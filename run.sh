#!/usr/bin/env bash
# Runs the app. JavaFX 21 needs JDK 17+; the default JDK on this machine is 11.
set -e
cd "$(dirname "$0")"
if [ -z "$JAVA_HOME" ] || ! "$JAVA_HOME/bin/java" -version 2>&1 | grep -qE 'version "(1[7-9]|2[0-9])'; then
  for c in "$HOME/.sdkman/candidates/java"/21* "$HOME/.sdkman/candidates/java"/17* "$HOME/.sdkman/candidates/java"/2[2-9]*; do
    [ -x "$c/bin/java" ] && export JAVA_HOME="$c" && break
  done
fi
echo "Using JAVA_HOME=$JAVA_HOME"
exec mvn -q javafx:run "$@"
