#!/usr/bin/env bash
# Assert that the Java version pinned in jitpack.yml still matches the `java`
# version in the catalog. Everything else in the repo reads the catalog --
# the Gradle modules through libs.versions.java, CI through
# java-version.sh -- but JitPack consumes its config as static YAML before
# any of that exists, so those literals have to be maintained by hand.
#
# Drift is otherwise silent until a release is published and JitPack builds
# it against the wrong JDK.
#
# Exit codes:
#   0  jitpack.yml agrees with the catalog
#   1  they disagree (both are printed), or neither could be parsed
set -euo pipefail

root="$(cd "$(dirname "$0")/../.." && pwd)"
jitpack="$root/jitpack.yml"

expected="$("$(dirname "$0")/java-version.sh")"

if [ ! -f "$jitpack" ]; then
  echo "error: $jitpack not found" >&2
  exit 1
fi

# `- openjdk17` under jdk:, and the `sdk install java 17.0.2-open` pair, all
# carry the major version and all have to agree with the catalog.
found="$(sed -n \
  -e 's/^[[:space:]]*-[[:space:]]*openjdk\([0-9][0-9]*\).*/\1/p' \
  -e 's/^[[:space:]]*-[[:space:]]*sdk \(install\|use\) java \([0-9][0-9]*\)\..*/\2/p' \
  "$jitpack")"

if [ -z "$found" ]; then
  echo "error: no JDK version found in $jitpack" >&2
  exit 1
fi

status=0
while read -r version; do
  if [ "$version" != "$expected" ]; then
    status=1
  fi
done <<<"$found"

if [ "$status" -ne 0 ]; then
  echo "error: jitpack.yml and gradle/libs.versions.toml disagree on the Java version" >&2
  echo "  catalog:     $expected" >&2
  echo "  jitpack.yml: $(echo "$found" | tr '\n' ' ')" >&2
  echo "Update jitpack.yml to match the catalog." >&2
  exit 1
fi

echo "jitpack.yml and the version catalog agree on Java $expected"
