#!/bin/sh
# Passes JVM flags from a mounted options file (secret-safe alternative to JAVA_TOOL_OPTIONS, which the JVM
# echoes into the log). Flags are whitespace-split, globbing disabled; paths with spaces are unsupported.
# Fails if the path is mis-mounted (not a regular file) or an explicitly set JAVA_OPTS_FILE does not exist.
set -eu
set -f

OPTS_FILE="${JAVA_OPTS_FILE:-/etc/xroad/catalog/jvm-options}"
EXTRA=""
if [ -e "$OPTS_FILE" ]; then
  if [ ! -f "$OPTS_FILE" ]; then
    echo "entrypoint: JAVA_OPTS_FILE path '$OPTS_FILE' exists but is not a regular file (mis-mounted?)" >&2
    exit 1
  fi
  EXTRA=$(cat "$OPTS_FILE")
elif [ -n "${JAVA_OPTS_FILE:-}" ]; then
  echo "entrypoint: JAVA_OPTS_FILE path '$OPTS_FILE' does not exist" >&2
  exit 1
fi

exec java $EXTRA "$@"
