#!/usr/bin/env bash
# Print the Java version the project builds against, read from the version
# catalog. `java` in gradle/libs.versions.toml is the single source of truth:
# every module's sourceCompatibility, targetCompatibility and jvmTarget read
# it, and CI installs the JDK this prints rather than a literal of its own.
#
# jitpack.yml is the one place that still carries a literal, because JitPack
# consumes it as static YAML before any of this is available.
#
# Exit codes:
#   0  the version is printed on stdout
#   1  the catalog is missing, or has no `java` version to read
set -euo pipefail

root="$(cd "$(dirname "$0")/../.." && pwd)"
catalog="$root/gradle/libs.versions.toml"

if [ ! -f "$catalog" ]; then
  echo "error: version catalog not found at $catalog" >&2
  exit 1
fi

version="$(sed -n 's/^java[[:space:]]*=[[:space:]]*"\([^"]*\)".*/\1/p' "$catalog")"

if [ -z "$version" ]; then
  echo "error: no 'java' version in $catalog" >&2
  exit 1
fi

echo "$version"
